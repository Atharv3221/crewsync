import type { CharId, DeleteOp, InsertOp, Op } from "./types";
import { compareIds, idKey, opId } from "./ids";

/** One character in the document, including deleted ones (tombstones). */
interface Node {
  readonly id: CharId;
  readonly value: string;
  deleted: boolean;
  /** The chunk holding this node; updated when chunks split. */
  chunk: Chunk;
}

/** A run of consecutive nodes in document order, with its number of visible ones. */
interface Chunk {
  readonly nodes: Node[];
  visible: number;
  /** Position of this chunk in `chunks`; renumbered when a chunk is inserted. */
  index: number;
}

/** A place between nodes: before `chunk.nodes[offset]` (or at the chunk's end). */
interface Slot {
  chunk: number;
  offset: number;
}

/**
 * Chunks split when they reach this size. Lookups walk the chunk list and then one chunk, so
 * position ↔ node costs O(n / CHUNK + CHUNK), about O(√n) for documents up to a few hundred
 * thousand characters, instead of scanning every node.
 */
const MAX_CHUNK = 512;

export class Rga {
  private readonly site: string;
  private clock = 0;
  /** The document in order, split into chunks. Never empty: holds at least one (maybe empty) chunk. */
  private readonly chunks: Chunk[] = [{ nodes: [], visible: 0, index: 0 }];
  private readonly index = new Map<string, Node>();
  private visibleCount = 0;
  private pending: Op[] = [];

  constructor(site: string) {
    this.site = site;
  }

  localInsert(pos: number, value: string): InsertOp {
    if (value.length !== 1) {
      throw new RangeError("insert exactly one UTF-16 code unit");
    }
    this.clock += 1;
    const op: InsertOp = {
      kind: "insert",
      counter: this.clock,
      site: this.site,
      origin: this.idAt(pos),
      value,
    };
    this.apply(op);
    return op;
  }

  localDelete(pos: number): DeleteOp {
    const target = this.visibleNode(pos).id;
    this.clock += 1;
    const op: DeleteOp = {
      kind: "delete",
      counter: this.clock,
      site: this.site,
      target,
    };
    this.apply(op);
    return op;
  }

  /**
   * Applies an op, or buffers it until its dependency arrives.
   * Safe to call twice.
   */
  apply(op: Op): void {
    if (this.tryApply(op)) {
      this.drainPending();
    } else {
      this.pending.push(op);
    }
  }

  private tryApply(op: Op): boolean {
    switch (op.kind) {
      case "insert":
        return this.integrateInsert(op);
      case "delete":
        return this.integrateDelete(op);
    }
  }

  /**
   * Goes to the slot right after the origin, skips every node whose id is greater than the new
   * one, and inserts there.
   */
  private integrateInsert(op: InsertOp): boolean {
    const id = opId(op);
    const key = idKey(id);
    let applied = this.index.has(key);
    if (!applied) {
      const slot = this.slotAfter(op.origin);
      if (slot !== null) {
        this.clock = Math.max(this.clock, op.counter);
        let next = this.nodeAt(slot);
        while (next !== undefined && compareIds(next.id, id) > 0) {
          slot.offset++;
          next = this.nodeAt(slot);
        }
        this.insertAt(slot, {
          id,
          value: op.value,
          deleted: false,
          chunk: this.chunks[0],
        });
        applied = true;
      }
    }
    return applied;
  }

  /** Waits (returns false) until the target arrives; deleting a tombstone again is a no-op. */
  private integrateDelete(op: DeleteOp): boolean {
    const node = this.index.get(idKey(op.target));
    if (node !== undefined) {
      this.clock = Math.max(this.clock, op.counter);
      if (!node.deleted) {
        node.deleted = true;
        node.chunk.visible--;
        this.visibleCount--;
      }
    }
    return node !== undefined;
  }

  private drainPending(): void {
    let progress = true;
    while (progress && this.pending.length > 0) {
      const before = this.pending.length;
      this.pending = this.pending.filter((op) => !this.tryApply(op));
      progress = this.pending.length < before;
    }
  }

  /** The slot right after `origin` (null origin = start), or null if the origin is unknown. */
  private slotAfter(origin: CharId | null): Slot | null {
    let slot: Slot | null = { chunk: 0, offset: 0 };
    if (origin !== null) {
      const node = this.index.get(idKey(origin));
      slot =
        node === undefined
          ? null
          : {
              chunk: node.chunk.index,
              offset: node.chunk.nodes.indexOf(node) + 1,
            };
    }
    return slot;
  }

  /** The node right after `slot`, moving the slot past chunk ends; undefined at document end. */
  private nodeAt(slot: Slot): Node | undefined {
    while (
      slot.offset >= this.chunks[slot.chunk].nodes.length &&
      slot.chunk < this.chunks.length - 1
    ) {
      slot.chunk++;
      slot.offset = 0;
    }
    return this.chunks[slot.chunk].nodes[slot.offset];
  }

  private insertAt(slot: Slot, node: Node): void {
    const chunk = this.chunks[slot.chunk];
    chunk.nodes.splice(slot.offset, 0, node);
    node.chunk = chunk;
    chunk.visible++;
    this.visibleCount++;
    this.index.set(idKey(node.id), node);
    if (chunk.nodes.length >= MAX_CHUNK) {
      this.split(chunk);
    }
  }

  /** Moves the second half of `chunk` into a new chunk right after it. */
  private split(chunk: Chunk): void {
    const moved = chunk.nodes.splice(chunk.nodes.length >> 1);
    const fresh: Chunk = { nodes: moved, visible: 0, index: chunk.index + 1 };
    for (const node of moved) {
      node.chunk = fresh;
      if (!node.deleted) {
        fresh.visible++;
      }
    }
    chunk.visible -= fresh.visible;
    this.chunks.splice(fresh.index, 0, fresh);
    for (let i = fresh.index + 1; i < this.chunks.length; i++) {
      this.chunks[i].index = i;
    }
  }

  text(): string {
    let out = "";
    for (const chunk of this.chunks) {
      for (const node of chunk.nodes) {
        if (!node.deleted) {
          out += node.value;
        }
      }
    }
    return out;
  }

  /**
   * ID of the visible character left of `pos` (null = start).
   *  Used as insert origin and cursor anchor.
   */
  idAt(pos: number): CharId | null {
    let id = null;
    if (pos > 0) {
      id = this.visibleNode(pos - 1).id;
    }
    return id;
  }

  /**
   * Inverse of idAt: the cursor position just right of character `id`, i.e. the number of
   * visible characters up to and including it. An unknown id maps to the end.
   */
  posOf(id: CharId | null): number {
    let pos = 0;
    if (id !== null) {
      const node = this.index.get(idKey(id));
      if (node === undefined) {
        pos = this.visibleCount;
      } else {
        for (let i = 0; i < node.chunk.index; i++) {
          pos += this.chunks[i].visible;
        }
        for (const other of node.chunk.nodes) {
          if (!other.deleted) {
            pos++;
          }
          if (other === node) {
            break;
          }
        }
      }
    }
    return pos;
  }

  /** Number of visible characters. */
  get length(): number {
    return this.visibleCount;
  }

  get pendingCount(): number {
    return this.pending.length;
  }

  private visibleNode(pos: number): Node {
    let found: Node | undefined;
    if (pos >= 0 && pos < this.visibleCount) {
      let remaining = pos;
      let c = 0;
      while (remaining >= this.chunks[c].visible) {
        remaining -= this.chunks[c].visible;
        c++;
      }
      for (const node of this.chunks[c].nodes) {
        if (!node.deleted) {
          if (remaining === 0) {
            found = node;
            break;
          }
          remaining--;
        }
      }
    }
    if (found === undefined) {
      throw new RangeError(`no visible character at position ${pos}`);
    }
    return found;
  }
}
