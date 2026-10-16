import { socketBase } from "../lib/endpoints";
import {
  parseServerMessage,
  type ClientMessage,
  type ServerMessage,
} from "./protocol";

export type ConnectionStatus = "connecting" | "online" | "offline";

export interface ConnectionHandlers {
  readonly onOpen: () => void;
  readonly onMessage: (message: ServerMessage) => void;
  readonly onStatus: (status: ConnectionStatus) => void;
  /** Called with the close code before any reconnect is scheduled; may call `close()`. */
  readonly onClose: (code: number) => void;
  /**
   * Called before each reconnect attempt. Resolves true to connect now, false to back off
   * and ask again later; may call `close()` to stop for good.
   */
  readonly beforeReconnect: () => Promise<boolean>;
}

/** Close code the server uses when the user lost access to the document (mirrors HTTP 403). */
export const ACCESS_REVOKED_CLOSE_CODE = 4403;

const BASE_DELAY_MS = 500;
const MAX_DELAY_MS = 10_000;

/** Browsers can't set headers on a WebSocket, so the token travels in the query string. */
export function socketUrl(docId: string, token: string): string {
  const query = `token=${encodeURIComponent(token)}`;
  return `${socketBase()}/ws/${encodeURIComponent(docId)}?${query}`;
}

/**
 * Closes `socket` without ever delivering another message. A socket still connecting is closed
 * once it opens: closing it earlier makes the browser log "closed before the connection is
 * established", which StrictMode's double mount would trigger on every page load.
 */
function closeSocket(socket: WebSocket): void {
  socket.onmessage = null;
  if (socket.readyState === WebSocket.CONNECTING) {
    socket.onopen = () => socket.close();
  } else {
    socket.close();
  }
}

export class Connection {
  private readonly url: string;
  private readonly handlers: ConnectionHandlers;
  private socket: WebSocket | null = null;
  private attempts = 0;
  private closed = false;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(url: string, handlers: ConnectionHandlers) {
    this.url = url;
    this.handlers = handlers;
  }

  open(): void {
    this.connect();
  }

  close(): void {
    this.closed = true;
    if (this.reconnectTimer !== null) {
      clearTimeout(this.reconnectTimer);
    }
    if (this.socket !== null) {
      closeSocket(this.socket);
    }
  }

  send(message: ClientMessage): boolean {
    let sent = false;
    if (this.socket !== null && this.socket.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify(message));
      sent = true;
    }
    return sent;
  }

  private connect(): void {
    this.handlers.onStatus("connecting");
    const socket = new WebSocket(this.url);
    this.socket = socket;

    socket.onopen = () => {
      this.attempts = 0;
      this.handlers.onStatus("online");
      this.handlers.onOpen();
    };

    socket.onmessage = (event) => {
      const message = parseServerMessage(String(event.data));
      if (message !== null) {
        this.handlers.onMessage(message);
      }
    };
    socket.onclose = (event) => {
      if (this.socket === socket) {
        this.socket = null;
      }
      this.handlers.onClose(event.code);
      if (!this.closed) {
        this.handlers.onStatus("offline");
        this.scheduleReconnect();
      }
    };
  }

  private scheduleReconnect(): void {
    const delay = Math.min(MAX_DELAY_MS, BASE_DELAY_MS * 2 ** this.attempts);
    this.attempts += 1;
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      void this.reconnect();
    }, delay);
  }

  private async reconnect(): Promise<void> {
    const ready = await this.handlers.beforeReconnect();
    if (!this.closed) {
      if (ready) {
        this.connect();
      } else {
        this.scheduleReconnect();
      }
    }
  }
}
