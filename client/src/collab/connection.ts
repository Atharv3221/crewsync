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
}

const BASE_DELAY_MS = 500;
const MAX_DELAY_MS = 10_000;

export function socketUrl(docId: string): string {
  const protocol = location.protocol === "https:" ? "wss:" : "ws:";
  return `${protocol}//${location.host}/ws/${encodeURIComponent(docId)}`;
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
    this.socket?.close();
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
    socket.onclose = () => {
      if (this.socket === socket) {
        this.socket = null;
      }
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
      this.connect();
    }, delay);
  }
}
