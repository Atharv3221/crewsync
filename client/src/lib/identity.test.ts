import { describe, expect, it } from "vitest";
import { colorFor, createSiteId, isValidDocId, newDocId } from "./idntity.ts";

describe("colorFor", () => {
    it("gives the same color for the same id", () => {
        expect(colorFor("user-123")).toBe(colorFor("user-123"));
    });

    it("always returns a hex color from the palette", () => {
        for (const id of ["", "a", "ab", "ba", "f3a9c2e1b7d4", "x".repeat(500)]) {
            expect(colorFor(id)).toMatch(/^#[0-9A-F]{6}$/);
        }
    });
});

describe("random ids", () => {
    it("createSiteId is 32 hex chars and unique per call", () => {
        const a = createSiteId();
        const b = createSiteId();
        expect(a).toMatch(/^[0-9a-f]{32}$/);
        expect(a).not.toBe(b);
    });

    it("newDocId is 16 hex chars and passes doc id validation", () => {
        const id = newDocId();
        expect(id).toMatch(/^[0-9a-f]{16}$/);
        expect(isValidDocId(id)).toBe(true);
    });
});

describe("isValidDocId", () => {
    it.each(["3f9a0c7be2d41a68", "team-notes", "project_x2", "a"])("accepts %s", (id) => {
        expect(isValidDocId(id)).toBe(true);
    });

    it.each(["", "my notes", "../admin", "a/b", "x".repeat(65), "<script>"])("rejects %s", (id) => {
        expect(isValidDocId(id)).toBe(false);
    });
});
