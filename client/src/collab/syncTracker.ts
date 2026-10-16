import { opKey, type Op } from "../crdt";

/**
 * Tracks synchronization state for a CRDT client, managing unconfirmed
 * local operations (the outbox) and the latest sequence number seen from the server.
 */
export class SyncTracker {
  private readonly outbox = new Map<string, Op>();
  private lastSeqSeen = 0;

  /** The highest sequence number received and processed from the server. */
  get lastSeq(): number {
    return this.lastSeqSeen;
  }

  /** The number of local operations currently waiting for server confirmation. */
  get unsynced(): number {
    return this.outbox.size;
  }

  /** Returns an array of all unconfirmed local operations currently in the outbox. */
  pending(): Op[] {
    return Array.from(this.outbox.values());
  }

  /**
   * Adds a local operation to the outbox, keyed by the op's own id, until the server echoes it.
   * @param op - The operation to track.
   */
  track(op: Op): void {
    this.outbox.set(opKey(op), op);
  }

  /**
   * Marks operations as confirmed by removing them from the outbox.
   * Our own ops echoed back by the server match by their own id; others' ops are ignored.
   * @param ops - The list of operations confirmed by the server.
   */
  confirm(ops: readonly Op[]): void {
    for (const op of ops) {
      this.outbox.delete(opKey(op));
    }
  }

  /**
   * Checks if an incoming sequence number skips ahead, indicating we missed operations.
   *
   * @param seq - The sequence number to evaluate.
   * @returns True if `seq` skips ops we never received.
   * @warning Must be called BEFORE advance().
   */
  hasGapBefore(seq: number): boolean {
    return seq > this.lastSeqSeen + 1;
  }

  /**
   * Updates the last seen sequence number to the given value,
   * ensuring it never moves backward.
   *
   * @param seq - The new sequence number to advance to.
   */
  advance(seq: number): void {
    this.lastSeqSeen = Math.max(this.lastSeqSeen, seq);
  }
}
