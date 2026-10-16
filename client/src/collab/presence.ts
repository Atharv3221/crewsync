import type { CharId, Rga } from "../crdt";
import { colorFor } from "../lib/identity";
import type { PresenceMessage } from "./protocol";

/** Represents a single person in the document, combining all their open tabs. */
export interface Peer {
  readonly userId: string;
  readonly name: string;
  readonly color: string;
  readonly tabs: number;
  readonly line: number;
}

/** Where one peer tab's caret is, as an offset into the current text. */
export interface RemoteCursor {
  readonly site: string;
  readonly userId: string;
  readonly name: string;
  readonly color: string;
  readonly pos: number;
}

/** One open tab of a peer, as last reported. */
interface PeerTab {
  readonly userId: string;
  readonly name: string;
  readonly cursor: CharId | null;
  readonly lastSeen: number;
}

/** Time in milliseconds after which a peer tab is considered inactive and pruned. */
const PEER_TIMEOUT_MS = 8_000;

/** Tracks other users' open tabs and turns them into a list of peers for the UI. */
export class PresenceTracker {
  private readonly ownUserId: string;
  private readonly tabs = new Map<string, PeerTab>();

  constructor(ownUserId: string) {
    this.ownUserId = ownUserId;
  }

  /**
   * Records a presence message from another user, ignoring all of this user's own tabs.
   *
   * @param message - The presence message containing peer details and cursor position.
   * @param now - The current timestamp in milliseconds.
   */
  receive(message: PresenceMessage, now: number): void {
    if (message.userId !== this.ownUserId) {
      this.tabs.set(message.site, {
        userId: message.userId,
        name: message.name,
        cursor: message.cursor,
        lastSeen: now,
      });
    }
  }

  /**
   * Drops every tab of `userId` at once, e.g. when they lost access to the document.
   *
   * @returns True if any tabs were removed, false otherwise.
   */
  removeUser(userId: string): boolean {
    let removed = false;
    for (const [site, tab] of this.tabs) {
      if (tab.userId === userId) {
        this.tabs.delete(site);
        removed = true;
      }
    }
    return removed;
  }

  /**
   * Drops tabs that have not been heard from recently (exceeding `PEER_TIMEOUT_MS`).
   *
   * @param now - The current timestamp in milliseconds.
   * @returns True if any tabs were removed, false otherwise.
   */
  prune(now: number): boolean {
    let removed = false;
    for (const [site, tab] of this.tabs) {
      if (now - tab.lastSeen > PEER_TIMEOUT_MS) {
        this.tabs.delete(site);
        removed = true;
      }
    }
    return removed;
  }

  /**
   * Groups active tabs by person and turns their cursors into line numbers.
   *
   * @param doc - The RGA document used to resolve cursor positions.
   * @returns One `Peer` per user.
   */
  peers(doc: Rga): Peer[] {
    const text = doc.text();
    const byUser = new Map<
      string,
      { name: string; tabs: number; line: number }
    >();
    for (const tab of this.tabs.values()) {
      const line = text.slice(0, doc.posOf(tab.cursor)).split("\n").length;
      const previous = byUser.get(tab.userId);
      byUser.set(tab.userId, {
        name: tab.name,
        tabs: (previous?.tabs ?? 0) + 1,
        line,
      });
    }
    return Array.from(byUser, ([userId, p]) => ({
      userId,
      name: p.name,
      color: colorFor(userId),
      tabs: p.tabs,
      line: p.line,
    }));
  }

  /**
   * One caret per peer tab, resolved against the current text so they follow concurrent edits.
   *
   * @param doc - The RGA document used to resolve cursor positions.
   * @returns Carets ordered by position.
   */
  cursors(doc: Rga): RemoteCursor[] {
    return Array.from(this.tabs, ([site, tab]) => ({
      site,
      userId: tab.userId,
      name: tab.name,
      color: colorFor(tab.userId),
      pos: doc.posOf(tab.cursor),
    })).sort((a, b) => a.pos - b.pos);
  }
}
