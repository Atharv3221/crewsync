import { Rga, type Op } from "../crdt";
import { createSiteId } from "../lib/identity";
import { Connection, socketUrl, type ConnectionStatus } from "./connection";
import { PresenceTracker, type Peer } from "./presence";
import type { ServerMessage } from "./protocol";
import { SyncTracker } from "./syncTracker";
import { TextareaBinding } from "./textareaBinding";

/** Represents a snapshot of the current collaboration state, sent to UI callbacks on updates. */
export interface CollabSnapshot {
    readonly status: ConnectionStatus;
    readonly peers: readonly Peer[];
    readonly unsynced: number;
    readonly length: number;
}

/** Configuration options required to initialize a collaboration client. */
export interface CollabOptions {
    readonly docId: string;
    readonly userId: string;
    readonly name: string;
    readonly onChange: (snapshot: CollabSnapshot) => void;
}

/** Interval in milliseconds for sending heartbeat signals and pruning inactive peers. */
const HEARTBEAT_MS = 3_000;

/** Minimum delay in milliseconds between throttled presence/cursor updates. */
const PRESENCE_THROTTLE_MS = 150;

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
        this.connection = new Connection(socketUrl(options.docId), {
            onOpen: () => this.handleOpen(),
            onMessage: (message) => this.handleMessage(message),
            onStatus: (status) => {
                this.status = status;
                this.emit();
            },
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
        if (this.heartbeatTimer !== null) clearInterval(this.heartbeatTimer);
        if (this.presenceTimer !== null) clearTimeout(this.presenceTimer);
        this.connection.close();
    }

    /**
     * Binds a DOM textarea element to the document and text binding system.
     * @param textarea - The HTML textarea element to attach, or null to detach.
     */
    attach(textarea: HTMLTextAreaElement | null): void {
        this.binding.attach(textarea);
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
        this.connection.send({ type: "hello", site: this.site, lastSeq: this.sync.lastSeq });
        for (const op of this.sync.pending()) {
            this.connection.send({ type: "op", op });
        }
        this.sendPresence();
    }

    /**
     * Processes incoming server messages, including initial doc state, operations, and peer presence.
     * @param message - The server message received.
     */
    private handleMessage(message: ServerMessage): void {
        switch (message.type) {
            case "init":
                this.receiveOps(message.ops);
                this.sync.advance(message.seq);
                break;
            case "op":
                if (this.sync.hasGapBefore(message.seq)) {
                    this.connection.send({ type: "hello", site: this.site, lastSeq: this.sync.lastSeq });
                }
                this.receiveOps([message.op]);
                this.sync.advance(message.seq);
                break;
            case "presence":
                this.presence.receive(message, Date.now());
                break;
        }
        this.emit();
    }

    /** Confirms local operations and applies remote operations to the document text binding. */
    private receiveOps(ops: readonly Op[]): void {
        this.sync.confirm(ops);
        this.binding.applyRemote(ops);
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
                peers: this.presence.peers(this.doc),
                unsynced: this.sync.unsynced,
                length: this.doc.text().length,
            });
        }
    }
}
