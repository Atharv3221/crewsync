/** Unique, permanent identity of one character. Never reused, never changes. */
export interface CharId {
  readonly counter: number;
  readonly site: string;
}

/**
 * Insert one character immediately after `origin`.
 * The op's own counter/site is the new character's id.
 * Note: null = start of document
 */
export interface InsertOp extends CharId {
  readonly kind: "insert";
  readonly origin: CharId | null;
  // exactly one UTF-16 code unit
  readonly value: string;
}

/**
 * Mark the character `target` as deleted (tombstone).
 * The op has its own counter/site from the same Lamport clock as inserts.
 */
export interface DeleteOp extends CharId {
  readonly kind: "delete";
  readonly target: CharId;
}

export type Op = InsertOp | DeleteOp;
