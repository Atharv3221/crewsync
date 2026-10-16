import { useCallback, useRef, useState } from "react";
import type { CollabSnapshot, ConnectionStatus, Peer } from "../collab";
import type { Doc, User } from "../lib/api";
import { routeHash } from "../lib/routes";
import { DocTitle } from "./DocTitle";
import { SharePanel } from "./SharePanel";
import { UserMenu } from "./UserMenu";
import { useDismiss } from "./useDismiss";
import "./TopBar.css";

interface TopBarProps extends Pick<CollabSnapshot, "peers" | "status"> {
  readonly doc: Doc;
  readonly user: User;
  /** The live title; it can differ from `doc.title` once anyone renames the doc. */
  readonly title: string;
  readonly onRenameDoc: (title: string) => Promise<void>;
  readonly onRename: (name: string) => Promise<void>;
  readonly onLogout: () => void;
  readonly onNewDoc: () => Promise<void>;
}

const STATUS_LABEL: Record<ConnectionStatus, string> = {
  connecting: "Connecting…",
  online: "Live",
  offline: "Offline · will sync on reconnect",
};

function initial(name: string): string {
  return name.trim().charAt(0).toUpperCase() || "?";
}

function peerTitle(peer: Peer): string {
  const tabs = peer.tabs > 1 ? ` · ${peer.tabs} tabs` : "";
  return `${peer.name} · line ${peer.line}${tabs}`;
}

/** Logo, document title, people present, connection status, share, new doc and the user menu. */
export function TopBar({
  doc,
  user,
  title,
  onRenameDoc,
  onRename,
  onLogout,
  onNewDoc,
  peers,
  status,
}: TopBarProps) {
  const [sharing, setSharing] = useState(false);
  const shareRef = useRef<HTMLDivElement>(null);
  const closeShare = useCallback(() => setSharing(false), []);
  useDismiss(shareRef, sharing, closeShare);

  return (
    <header className="topbar">
      <div className="topbar-left">
        <a className="logo" href={routeHash({ page: "home" })} title="My docs">
          CrewSync
        </a>
        <DocTitle
          title={title}
          editable={doc.role === "OWNER"}
          onRename={onRenameDoc}
        />
      </div>

      <div className="topbar-right">
        <ul className="peers" aria-label="People in this document">
          {peers.map((peer) => (
            <li
              key={peer.userId}
              className="peer"
              style={{ backgroundColor: peer.color }}
              title={peerTitle(peer)}
            >
              {initial(peer.name)}
            </li>
          ))}
        </ul>

        <span className={`status status-${status}`}>
          <span className="status-dot" />
          {STATUS_LABEL[status]}
        </span>

        <div className="share" ref={shareRef}>
          <button
            type="button"
            className="primary"
            aria-expanded={sharing}
            onClick={() => setSharing(!sharing)}
          >
            Share
          </button>
          {sharing && (
            <SharePanel
              docId={doc.id}
              role={doc.role}
              currentUserId={user.id}
            />
          )}
        </div>
        <button type="button" onClick={() => void onNewDoc()}>
          New doc
        </button>
        <UserMenu user={user} onRename={onRename} onLogout={onLogout} />
      </div>
    </header>
  );
}
