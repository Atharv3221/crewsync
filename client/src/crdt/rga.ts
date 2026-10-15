import type { CharId, DeleteOp, InsertOp, Op } from "./types";
import { compareIds, idKey, sameId } from "./ids";

/** One character in the document, including deleted ones (tombstones). */
interface Node {
  readonly id: CharId;
  readonly value: string;
  deleted: boolean;
}

export class Rga {
  private readonly site: string;
  private clock = 0;
  private readonly nodes: Node[] = [];
  private readonly index = new Map<string, Node>();
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
      id: { counter: this.clock, site: this.site },
      origin: this.idAt(pos),
      value,
    };
    this.apply(op);
    return op;
  }

  localDelete(pos: number): DeleteOp {
    const op: DeleteOp = {
      kind: "delete",
      target: this.visibleNode(pos).id,
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

  private integrateInsert(op: InsertOp): boolean {
    const key = idKey(op.id);
    let applied = this.index.has(key);
    if (!applied) {
      let slot = this.slotAfter(op.origin);
      if (slot >= 0) {
        this.clock = Math.max(this.clock, op.id.counter);
        while (
          slot < this.nodes.length &&
          compareIds(this.nodes[slot].id, op.id) > 0
        ) {
          slot++;
        }
        const node: Node = {
          id: op.id,
          value: op.value,
          deleted: false,
        };
        this.nodes.splice(slot, 0, node);
        this.index.set(key, node);
        applied = true;
      }
    }
    return applied;
  }

  private integrateDelete(op: DeleteOp): boolean {
    const node = this.index.get(idKey(op.target));
    if (node !== undefined) {
      node.deleted = true;
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

  private slotAfter(origin: CharId | null): number {
    let slot = 0;
    if (origin !== null) {
      const node = this.index.get(idKey(origin));
      if (node === undefined) {
        slot = -1;
      } else {
        slot = this.nodes.indexOf(node) + 1;
      }
    }
    return slot;
  }

  text(): string {
    let out = "";
    for (const node of this.nodes) {
      if (!node.deleted) {
        out += node.value;
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

  /** Inverse of idAt: the cursor position just right of character `id`. */
  posOf(id: CharId | null): number {
    let pos = 0;
    if (id !== null) {
      for (const node of this.nodes) {
        if (!node.deleted) {
          pos++;
        }
        if (sameId(node.id, id)) {
          break;
        }
      }
    }
    return pos;
  }

  /** Number of visible characters; cheaper than text().length (no string building). */
  get length(): number {
    let count = 0;
    for (const node of this.nodes) {
      if (!node.deleted) {
        count += 1;
      }
    }
    return count;
  }

  get pendingCount(): number {
    return this.pending.length;
  }

  private visibleNode(pos: number): Node {
    let seen = 0;
    let found: Node | undefined;
    for (const node of this.nodes) {
      if (!node.deleted) {
        if (seen === pos) {
          found = node;
          break;
        }
        seen++;
      }
    }
    if (found === undefined) {
      throw new RangeError(`no visible character at position ${pos}`);
    }
    return found;
  }
}
