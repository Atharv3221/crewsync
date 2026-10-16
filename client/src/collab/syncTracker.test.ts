import { describe, expect, it } from "vitest";
import { Rga, type Op } from "../crdt";
import { SyncTracker } from "./syncTracker";

/** Real ops from a real document, so keys match exactly what the app produces. */
function makeOps(site: string, text: string): Op[] {
  const doc = new Rga(site);
  const ops: Op[] = [];
  for (let i = 0; i < text.length; i++) {
    ops.push(doc.localInsert(i, text[i]));
  }
  return ops;
}

describe("SyncTracker", () => {
  it("starts empty at seq 0", () => {
    const sync = new SyncTracker();
    expect(sync.lastSeq).toBe(0);
    expect(sync.unsynced).toBe(0);
    expect(sync.pending()).toEqual([]);
  });

  it("keeps tracked ops in order until confirmed", () => {
    const sync = new SyncTracker();
    const ops = makeOps("A", "abc");
    ops.forEach((op) => sync.track(op));
    expect(sync.unsynced).toBe(3);
    expect(sync.pending()).toEqual(ops);
  });

  it("counts the same op once", () => {
    const sync = new SyncTracker();
    const [op] = makeOps("A", "a");
    sync.track(op);
    sync.track(op);
    expect(sync.unsynced).toBe(1);
  });

  it("confirm removes own echoed ops and ignores others' ops", () => {
    const sync = new SyncTracker();
    const mine = makeOps("A", "ab");
    const theirs = makeOps("B", "xy");
    mine.forEach((op) => sync.track(op));

    sync.confirm(theirs);
    expect(sync.unsynced).toBe(2);

    sync.confirm([mine[0], ...theirs]);
    expect(sync.pending()).toEqual([mine[1]]);
  });

  it("matches the echo of a delete by the delete's own id", () => {
    const sync = new SyncTracker();
    const doc = new Rga("A");
    const insert = doc.localInsert(0, "x");
    const del = doc.localDelete(0);
    sync.track(insert);
    sync.track(del);

    sync.confirm([insert]);
    expect(sync.pending()).toEqual([del]);
    sync.confirm([del]);
    expect(sync.unsynced).toBe(0);
  });

  it("keeps two sites' deletes of the same character apart", () => {
    const a = new Rga("A");
    const b = new Rga("B");
    const insert = a.localInsert(0, "x");
    b.apply(insert);
    const mine = a.localDelete(0);
    const theirs = b.localDelete(0);

    const sync = new SyncTracker();
    sync.track(mine);
    sync.confirm([theirs]);
    expect(sync.pending()).toEqual([mine]);
  });

  it("detects gaps in the server sequence", () => {
    const sync = new SyncTracker();
    expect(sync.hasGapBefore(1)).toBe(false);
    expect(sync.hasGapBefore(3)).toBe(true);

    sync.advance(5);
    expect(sync.lastSeq).toBe(5);
    expect(sync.hasGapBefore(6)).toBe(false);
    // duplicate, not a gap
    expect(sync.hasGapBefore(5)).toBe(false);
    expect(sync.hasGapBefore(8)).toBe(true);
  });

  it("never moves lastSeq backwards", () => {
    const sync = new SyncTracker();
    sync.advance(10);
    sync.advance(7);
    expect(sync.lastSeq).toBe(10);
  });
});
