import type { CharId, Op } from "../crdt";

/**
 * First message after connecting.
 * Send everything after lastSeq.
 */
export interface HelloMessage {
  readonly type: "hello";
  readonly lastSeq: number;
}

/**
 * A local edit. The server assigns it a seq and broadcasts it. A duplicate
 * (same op_key) is answered to the sender alone with its original seq.
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

/** An op together with the seq the server stored it under. */
export interface SequencedOp {
  readonly seq: number;
  readonly op: Op;
}

/**
 * Reply to {@linkcode HelloMessage}, all ops after the client's
 * last sequence. `seq` is the server's latest seq.
 */
export interface InitMessage {
  readonly type: "init";
  readonly seq: number;
  readonly ops: readonly SequencedOp[];
}

export interface OpInMessage {
  readonly type: "op";
  readonly seq: number;
  readonly op: Op;
}

/** Every tab of `userId` is gone, e.g. because the owner removed them: drop their presence. */
export interface LeaveMessage {
  readonly type: "leave";
  readonly userId: string;
}

/** The doc was renamed (by anyone, including this user). */
export interface TitleMessage {
  readonly type: "title";
  readonly title: string;
  readonly version: number;
}

export type ServerMessage =
  InitMessage | OpInMessage | PresenceMessage | LeaveMessage | TitleMessage;

const SERVER_TYPES: ReadonlySet<unknown> = new Set([
  "init",
  "op",
  "presence",
  "leave",
  "title",
]);

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
      SERVER_TYPES.has(data.type)
    ) {
      result = data as ServerMessage;
    }
  } catch {
    result = null;
  }
  return result;
}
