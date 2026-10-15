import type { CharId, Op } from "../crdt";

/**
 * First message after connecting.
 * Send everything after lastSeq.
 */
export interface HelloMessage {
  readonly type: "hello";
  readonly site: string;
  readonly lastSeq: number;
}

/**
 * A local edit. The server assigns it a seq and
 * broadcasts it.
 */
export interface OpOutMessage {
  readonly type: "op";
  readonly op: Op;
}

export interface PresenceMessage {
  readonly type: "presence";
  readonly site: string;
  readonly userId: string;
  readonly name: string;
  readonly cursor: CharId | null;
}

export type ClientMessage = HelloMessage | OpOutMessage | PresenceMessage;

/**
 * Reply to {@linkcode HelloMessage}, all ops after the client's
 * last sequence.
 */
export interface InitMessage {
  readonly type: "init";
  readonly seq: number;
  readonly ops: readonly Op[];
}

export interface OpInMessage {
  readonly type: "op";
  readonly seq: number;
  readonly op: Op;
}

export type ServerMessage = InitMessage | OpInMessage | PresenceMessage;

/**
 * Parses raw WebSocket text into a {@linkcode ServerMessage},
 * or null if it isn't one.
 */
export function parseServerMessage(raw: string): ServerMessage | null {
  let result: ServerMessage | null = null;
  try {
    const data: unknown = JSON.parse(raw);
    if (
      typeof data === "object" &&
      data !== null &&
      "type" in data &&
      (data.type === "init" || data.type === "op" || data.type === "presence")
    ) {
      result = data as ServerMessage;
    }
  } catch {
    result = null;
  }
  return result;
}
