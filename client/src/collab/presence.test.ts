import { describe, expect, it } from "vitest";
import { Rga, type CharId } from "../crdt";
import { colorFor } from "../lib/identity";
import { PresenceTracker } from "./presence";
import type { PresenceMessage } from "./protocol";

function presence(site: string, userId: string, name: string,
    cursor: CharId | null = null): PresenceMessage {
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
        tracker.receive(presence("s1", "u1", "Start"), 0);               // null cursor
        tracker.receive(presence("s2", "u2", "Line2", doc.idAt(4)), 0);  // after "ab\nc"
        tracker.receive(presence("s3", "u3", "Line3", doc.idAt(8)), 0);  // end of doc

        const lineOf = (id: string) => tracker.peers(doc).find((p) => p.userId === id)?.line;
        expect(lineOf("u1")).toBe(1);
        expect(lineOf("u2")).toBe(2);
        expect(lineOf("u3")).toBe(3);
    });

    it("prunes tabs silent for more than 8 seconds", () => {
        const tracker = new PresenceTracker("me");
        tracker.receive(presence("s1", "u1", "Asha"), 0);

        expect(tracker.prune(8_000)).toBe(false); // exactly 8 s: still alive
        expect(tracker.peers(doc)).toHaveLength(1);

        expect(tracker.prune(8_001)).toBe(true);
        expect(tracker.peers(doc)).toEqual([]);
    });
});
