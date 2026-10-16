import { describe, expect, it } from "vitest";
import { colorFor, createSiteId } from "./identity.ts";

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

describe("createSiteId", () => {
  it("is 32 hex chars and unique per call", () => {
    const a = createSiteId();
    const b = createSiteId();
    expect(a).toMatch(/^[0-9a-f]{32}$/);
    expect(a).not.toBe(b);
  });
});
