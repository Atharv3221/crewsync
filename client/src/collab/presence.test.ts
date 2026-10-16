import { describe, expect, it } from "vitest";
import { Rga, type CharId } from "../crdt";
import { colorFor } from "../lib/identity";
import { PresenceTracker } from "./presence";
import type { PresenceMessage } from "./protocol";

function presence(
  site: string,
  userId: string,
  name: string,
  cursor: CharId | null = null,
): PresenceMessage {
  return { type: "presence", site, userId, name, cursor };
}

function docWith(text: string): Rga {
  const doc = new Rga("doc");
  for (let i = 0; i < text.length; i++) {
    doc.localInsert(i, text[i]);
  }
  return doc;
}

describe("PresenceTracker", () => {
  const doc = docWith("ab\ncd\nef");

  it("ignores every tab of the current user", () => {
    const tracker = new PresenceTracker("me");
    tracker.receive(presence("s1", "me", "Me"), 0);
    tracker.receive(presence("s2", "me", "Me"), 0);
    expect(tracker.peers(doc)).toEqual([]);
  });

  it("groups tabs of the same user into one peer", () => {
    const tracker = new PresenceTracker("me");
    tracker.receive(presence("s1", "u1", "Asha"), 0);
    tracker.receive(presence("s2", "u1", "Asha"), 0);
    tracker.receive(presence("s3", "u2", "Ravi"), 0);

    const peers = tracker.peers(doc);
    expect(peers).toHaveLength(2);
    expect(peers.find((p) => p.userId === "u1")?.tabs).toBe(2);
    expect(peers.find((p) => p.userId === "u2")?.tabs).toBe(1);
  });

  it("updates a tab instead of duplicating it", () => {
    const tracker = new PresenceTracker("me");
    tracker.receive(presence("s1", "u1", "Asha"), 0);
    tracker.receive(presence("s1", "u1", "Asha K"), 100);

    const [peer] = tracker.peers(doc);
    expect(peer.tabs).toBe(1);
    expect(peer.name).toBe("Asha K");
  });

  it("uses the stable color for the user", () => {
    const tracker = new PresenceTracker("me");
    tracker.receive(presence("s1", "u1", "Asha"), 0);
    expect(tracker.peers(doc)[0].color).toBe(colorFor("u1"));
  });

  it("turns the cursor into a 1-based line number", () => {
    const tracker = new PresenceTracker("me");
    tracker.receive(presence("s1", "u1", "Start"), 0); // null cursor
    tracker.receive(presence("s2", "u2", "Line2", doc.idAt(4)), 0); // after "ab\nc"
    tracker.receive(presence("s3", "u3", "Line3", doc.idAt(8)), 0); // end of doc

    const lineOf = (id: string) =>
      tracker.peers(doc).find((p) => p.userId === id)?.line;
    expect(lineOf("u1")).toBe(1);
    expect(lineOf("u2")).toBe(2);
    expect(lineOf("u3")).toBe(3);
  });

  it("removes every tab of a user at once", () => {
    const tracker = new PresenceTracker("me");
    tracker.receive(presence("s1", "grace", "Grace"), 0);
    tracker.receive(presence("s2", "grace", "Grace"), 0);
    tracker.receive(presence("s3", "ada", "Ada"), 0);

    expect(tracker.removeUser("grace")).toBe(true);
    expect(tracker.peers(doc).map((peer) => peer.userId)).toEqual(["ada"]);
    expect(tracker.removeUser("grace")).toBe(false);
  });

  it("prunes tabs silent for more than 8 seconds", () => {
    const tracker = new PresenceTracker("me");
    tracker.receive(presence("s1", "u1", "Asha"), 0);

    expect(tracker.prune(8_000)).toBe(false); // exactly 8 s: still alive
    expect(tracker.peers(doc)).toHaveLength(1);

    expect(tracker.prune(8_001)).toBe(true);
    expect(tracker.peers(doc)).toEqual([]);
  });

  it("reports one caret per tab, ordered by position", () => {
    const tracker = new PresenceTracker("me");
    tracker.receive(presence("s1", "u1", "Asha", doc.idAt(8)), 0); // end of doc
    tracker.receive(presence("s2", "u2", "Ravi"), 0); // null cursor: start
    tracker.receive(presence("s3", "u1", "Asha", doc.idAt(3)), 0); // second tab, after "ab\n"
    tracker.receive(presence("s4", "me", "Me", doc.idAt(5)), 0); // own tab: hidden

    expect(tracker.cursors(doc)).toEqual([
      { site: "s2", userId: "u2", name: "Ravi", color: colorFor("u2"), pos: 0 },
      { site: "s3", userId: "u1", name: "Asha", color: colorFor("u1"), pos: 3 },
      { site: "s1", userId: "u1", name: "Asha", color: colorFor("u1"), pos: 8 },
    ]);
  });

  it("keeps a caret on its character when text is inserted before it", () => {
    const local = docWith("hello");
    const tracker = new PresenceTracker("me");
    tracker.receive(presence("s1", "u1", "Asha", local.idAt(2)), 0); // after "he"
    expect(tracker.cursors(local)[0].pos).toBe(2);

    local.localInsert(0, "\n");
    local.localInsert(0, "X");

    expect(tracker.cursors(local)[0].pos).toBe(4);
  });
});
