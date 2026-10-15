const USER_ID_KEY = "crewsync.userId";
const NAME_KEY = "crewsync.name";
const DOC_ID_PATTERN = /^[A-Za-z0-9_-]{1,64}$/;

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

export function newDocId(): string {
  return randomHex(8);
}

export function isValidDocId(id: string): boolean {
  return DOC_ID_PATTERN.test(id);
}

export function currentDocId(): string {
  const fromHash = window.location.hash.slice(1);
  let docId = fromHash;
  if (!isValidDocId(fromHash)) {
    docId = newDocId();
    history.replaceState(null, "", `#${docId}`);
  }
  return docId;
}

function readStorage(key: string): string | null {
  let value: string | null;
  try {
    value = localStorage.getItem(key);
  } catch {
    value = null;
  }
  return value;
}

function writeStorage(key: string, value: string): void {
  try {
    localStorage.setItem(key, value);
  } catch {
    // storage blocked: the value just won't survive a reload
  }
}

/** Stable per-browser user ID. */
export function getUserId(): string {
  let userId = readStorage(USER_ID_KEY);
  if (userId === null) {
    userId = randomHex(16);
    writeStorage(USER_ID_KEY, userId);
  }
  return userId;
}

export function getUserName(): string {
  let name = readStorage(NAME_KEY);
  if (name === null) {
    name = `Guest ${100 + Math.floor(Math.random() * 900)}`;
    writeStorage(NAME_KEY, name);
  }
  return name;
}

export function setUserName(raw: string): string {
  const cleaned = raw.trim().slice(0, 32) || getUserName();
  writeStorage(NAME_KEY, cleaned);
  return cleaned;
}

export function colorFor(id: string): string {
  let hash = 0;
  for (let i = 0; i < id.length; i++) {
    hash = (hash * 31 + id.charCodeAt(i)) >>> 0;
  }
  return PEER_COLORS[hash % PEER_COLORS.length];
}
