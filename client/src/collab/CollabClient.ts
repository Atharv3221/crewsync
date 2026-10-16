import { Rga, type Op } from "../crdt";
import { ApiError, getDoc } from "../lib/api";
import { createSiteId } from "../lib/identity";
import {
  ACCESS_REVOKED_CLOSE_CODE,
  Connection,
  socketUrl,
  type ConnectionStatus,
} from "./connection";
import { PresenceTracker, type Peer, type RemoteCursor } from "./presence";
import type { SequencedOp, ServerMessage } from "./protocol";
import { SyncTracker } from "./syncTracker";
import { TextareaBinding } from "./textareaBinding";

/**
 * Why the client stopped for good: removed from the doc, the doc is gone, or the session expired
 * (the api layer has already sent the user to the login page).
 */
export type AccessLoss = "forbidden" | "missing" | "unauthorized";

/** Represents a snapshot of the current collaboration state, sent to UI callbacks on updates. */
export interface CollabSnapshot {
  readonly status: ConnectionStatus;
  readonly accessLost: AccessLoss | null;
  readonly peers: readonly Peer[];
  readonly cursors: readonly RemoteCursor[];
  readonly unsynced: number;
  readonly length: number;
  readonly text: string;
  /** The newest title heard from the server, or null until a rename or access check reports one. */
  readonly title: string | null;
  readonly titleVersion: number;
}

/** Configuration options required to initialize a collaboration client. */
export interface CollabOptions {
  readonly docId: string;
  readonly userId: string;
  readonly name: string;
  readonly token: string;
  readonly onChange: (snapshot: CollabSnapshot) => void;
}

/** Interval in milliseconds for sending heartbeat signals and pruning inactive peers. */
const HEARTBEAT_MS = 3_000;

/** Minimum delay in milliseconds between throttled presence/cursor updates. */
const PRESENCE_THROTTLE_MS = 150;

const LOSS_BY_STATUS: ReadonlyMap<number, AccessLoss> = new Map([
  [401, "unauthorized"],
  [403, "forbidden"],
  [404, "missing"],
]);

/** The access loss a failed access check means, or null for errors worth retrying. */
function lossFor(error: unknown): AccessLoss | null {
  return error instanceof ApiError
    ? (LOSS_BY_STATUS.get(error.status) ?? null)
    : null;
}

/**
 * Orchestrates real-time document collaboration, managing CRDT synchronization,
 * network connection lifecycle, text bindings, and user presence.
 */
export class CollabClient {
  private readonly options: CollabOptions;
  private readonly site = createSiteId();
  private readonly doc: Rga;
  private readonly binding: TextareaBinding;
  private readonly sync = new SyncTracker();
  private readonly presence: PresenceTracker;
  private readonly connection: Connection;

  private name: string;
  private status: ConnectionStatus = "connecting";
  private accessLost: AccessLoss | null = null;
  private title: string | null = null;
  private titleVersion = -1;
  private disposed = false;
  private heartbeatTimer: ReturnType<typeof setInterval> | null = null;
  private presenceTimer: ReturnType<typeof setTimeout> | null = null;

  /**
   * Creates an instance of CollabClient.
   * @param options - Configuration options containing document ID, user identity, and change callbacks.
   */
  constructor(options: CollabOptions) {
    this.options = options;
    this.name = options.name;
    this.doc = new Rga(this.site);
    this.binding = new TextareaBinding(this.doc);
    this.presence = new PresenceTracker(options.userId);
    this.connection = new Connection(socketUrl(options.docId, options.token), {
      onOpen: () => this.handleOpen(),
      onMessage: (message) => this.handleMessage(message),
      onStatus: (status) => {
        this.status = status;
        this.emit();
      },
      onClose: (code) => {
        if (code === ACCESS_REVOKED_CLOSE_CODE) {
          this.loseAccess("forbidden");
        }
      },
      beforeReconnect: () => this.checkAccess(),
    });
  }

  // ---------- lifecycle ----------

  /** Starts the collaboration client by opening the socket and starting periodic heartbeats. */
  start(): void {
    this.connection.open();
    this.heartbeatTimer = setInterval(() => {
      this.sendPresence();
      if (this.presence.prune(Date.now())) {
        this.emit();
      }
    }, HEARTBEAT_MS);
  }

  /** Cleans up all resources, clears active timers, and closes the network connection. */
  dispose(): void {
    this.disposed = true;
    this.stop();
  }

  private stop(): void {
    if (this.heartbeatTimer !== null) clearInterval(this.heartbeatTimer);
    if (this.presenceTimer !== null) clearTimeout(this.presenceTimer);
    this.connection.close();
  }

  // ---------- access ----------

  /**
   * Asks the API whether the doc is still ours before reconnecting. Access errors stop the client;
   * anything else (network down, server error) resolves false so the backoff continues.
   */
  private async checkAccess(): Promise<boolean> {
    let ready = false;
    try {
      const doc = await getDoc(this.options.docId);
      this.updateTitle(doc.title, doc.titleVersion);
      ready = true;
    } catch (error) {
      const loss = lossFor(error);
      if (loss !== null) {
        this.loseAccess(loss);
      }
    }
    return ready;
  }

  /** Stops reconnecting for good and tells the UI why. */
  private loseAccess(loss: AccessLoss): void {
    this.accessLost = loss;
    this.stop();
    this.emit();
  }

  /**
   * Binds a DOM textarea element to the document and text binding system.
   * @param textarea - The HTML textarea element to attach, or null to detach.
   */
  attach(textarea: HTMLTextAreaElement | null): void {
    this.binding.attach(textarea);
  }

  /**
   * Shows a title this user just saved, unless a newer one has already arrived: the HTTP reply and
   * the broadcasts travel separately, so they can come in either order.
   */
  setTitle(title: string, version: number): void {
    if (this.updateTitle(title, version)) {
      this.emit();
    }
  }

  /** Keeps the title with the highest version; returns whether it changed. */
  private updateTitle(title: string, version: number): boolean {
    const newer = version > this.titleVersion;
    if (newer) {
      this.title = title;
      this.titleVersion = version;
    }
    return newer;
  }

  /**
   * Updates the user's display name and immediately broadcasts a presence update.
   * @param name - The new user name.
   */
  setName(name: string): void {
    this.name = name;
    this.sendPresence();
  }

  // ---------- textarea events ----------

  /** Handles regular typing or text input changes in the bound textarea. */
  handleInput(): void {
    if (!this.binding.isComposing) {
      this.sendLocal(this.binding.readLocalEdit());
    }
  }

  /** Signals the start of an IME composition session (e.g., CJK input). */
  handleCompositionStart(): void {
    this.binding.startComposition();
  }

  /** Signals the end of an IME composition session and flushes generated changes. */
  handleCompositionEnd(): void {
    this.sendLocal(this.binding.endComposition());
  }

  /** Handles text selection changes, triggering a throttled presence update. */
  handleSelect(): void {
    this.schedulePresence();
  }

  // ---------- sync ----------

  /** Tracks local operations, sends them over the network, and schedules presence updates. */
  private sendLocal(ops: readonly Op[]): void {
    for (const op of ops) {
      this.sync.track(op);
      this.connection.send({ type: "op", op }); // no-op while offline; re-sent on reconnect
    }
    this.schedulePresence();
    this.emit();
  }

  /** Handles opening the socket connection, handshaking with the server, and syncing pending state. */
  private handleOpen(): void {
    this.sendHello();
    for (const op of this.sync.pending()) {
      this.connection.send({ type: "op", op });
    }
    this.sendPresence();
  }

  /** Tells the server the last sequence number we've seen, so it can replay what we missed. */
  private sendHello(): void {
    this.connection.send({ type: "hello", lastSeq: this.sync.lastSeq });
  }

  /**
   * Processes incoming server messages, including initial doc state, operations, and peer presence.
   * @param message - The server message received.
   */
  private handleMessage(message: ServerMessage): void {
    switch (message.type) {
      case "init":
        this.receiveOps(message.ops);
        break;
      case "op":
        if (this.sync.hasGapBefore(message.seq)) {
          this.sendHello();
        }
        this.receiveOps([message]);
        break;
      case "presence":
        this.presence.receive(message, Date.now());
        break;
      case "title":
        this.updateTitle(message.title, message.version);
        break;
      case "leave":
        this.presence.removeUser(message.userId);
        break;
    }
    this.emit();
  }

  /** Confirms local operations, applies remote ones to the text binding and advances lastSeq. */
  private receiveOps(entries: readonly SequencedOp[]): void {
    const ops = entries.map((entry) => entry.op);
    this.sync.confirm(ops);
    this.binding.applyRemote(ops);
    for (const entry of entries) {
      this.sync.advance(entry.seq);
    }
  }

  // ---------- presence ----------

  /** Schedules a throttled presence broadcast to avoid overwhelming the socket with cursor moves. */
  private schedulePresence(): void {
    if (this.presenceTimer === null) {
      this.presenceTimer = setTimeout(() => {
        this.presenceTimer = null;
        this.sendPresence();
      }, PRESENCE_THROTTLE_MS);
    }
  }

  /** Broadcasts the current user's cursor position and presence info to the server. */
  private sendPresence(): void {
    this.connection.send({
      type: "presence",
      site: this.site,
      userId: this.options.userId,
      name: this.name,
      cursor: this.binding.cursor(),
    });
  }

  // ---------- output ----------

  /** Triggers the `onChange` callback with a fresh snapshot of the collaboration state. */
  private emit(): void {
    if (!this.disposed) {
      this.options.onChange({
        status: this.status,
        accessLost: this.accessLost,
        peers: this.presence.peers(this.doc),
        cursors: this.presence.cursors(this.doc),
        unsynced: this.sync.unsynced,
        length: this.doc.length,
        text: this.doc.text(),
        title: this.title,
        titleVersion: this.titleVersion,
      });
    }
  }
}
