const PEER_COLORS = [
  "#534AB7",
  "#0F6E56",
  "#993C1D",
  "#993556",
  "#185FA5",
  "#3B6D11",
  "#854F0B",
  "#A32D2D",
] as const;

/** Random hex string. getRandomValues works on plain http too (unlike randomUUID). */
function randomHex(byteCount: number): string {
  const bytes = new Uint8Array(byteCount);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
}

/**
 * A new replica ID. Call once per page load; never store it.
 */
export function createSiteId(): string {
  return randomHex(16);
}

export function colorFor(id: string): string {
  let hash = 0;
  for (let i = 0; i < id.length; i++) {
    hash = (hash * 31 + id.charCodeAt(i)) >>> 0;
  }
  return PEER_COLORS[hash % PEER_COLORS.length];
}
