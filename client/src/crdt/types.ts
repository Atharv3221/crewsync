/** Unique, permanent identity of one character. Never reused, never changes. */
export interface CharId {
    readonly counter: number;
    readonly site: string;
}

/**
 * Insert one character immediately after `origin`.
 * Note: null = start of document
 */
export interface InsertOp {
    readonly kind: "insert";
    readonly id: CharId;
    readonly origin: CharId | null;
    // exactly one UTF-16 code unit
    readonly value: string;
}

/** Mark the character `target` as deleted (tombstone). */
export interface DeleteOp {
    readonly kind: "delete";
    readonly target: CharId;
}

export type Op = InsertOp | DeleteOp
