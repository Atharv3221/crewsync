import { useState } from "react";
import type { CollabSnapshot, ConnectionStatus, Peer } from "../collab";
import { newDocId } from "../lib/identity";
import "./TopBar.css";

interface TopBarProps extends Pick<CollabSnapshot, "peers" | "status"> {
  readonly docId: string;
  readonly name: string;
  readonly onRename: (name: string) => void;
}

const STATUS_LABEL: Record<ConnectionStatus, string> = {
  connecting: "Connecting…",
  online: "Live",
  offline: "Offline · will sync on reconnect",
};

const COPIED_MS = 1_500;

/** Copies with the Clipboard API; returns false where it isn't available (plain http). */
async function copyText(text: string): Promise<boolean> {
  let copied = false;
  if (window.isSecureContext && navigator.clipboard !== undefined) {
    try {
      await navigator.clipboard.writeText(text);
      copied = true;
    } catch {
      copied = false;
    }
  }
  return copied;
}

function initial(name: string): string {
  return name.trim().charAt(0).toUpperCase() || "?";
}

function peerTitle(peer: Peer): string {
  const tabs = peer.tabs > 1 ? ` · ${peer.tabs} tabs` : "";
  return `${peer.name} · line ${peer.line}${tabs}`;
}

/** Logo, document id, people present, connection status, name, share and new-doc actions. */
export function TopBar({ docId, name, onRename, peers, status }: TopBarProps) {
  const [draft, setDraft] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  const commitName = (): void => {
    if (draft !== null && draft.trim() !== "") {
      onRename(draft);
    }
    setDraft(null);
  };

  const share = async (): Promise<void> => {
    const url = location.href;
    if (await copyText(url)) {
      setCopied(true);
      setTimeout(() => setCopied(false), COPIED_MS);
    } else {
      window.prompt("Copy this link:", url);
    }
  };

  const newDoc = (): void => {
    location.hash = newDocId();
  };

  return (
    <header className="topbar">
      <div className="topbar-left">
        <span className="logo">Crewrite</span>
        <span className="doc-id" title="Document ID">
          #{docId}
        </span>
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

        <input
          className="name-input"
          aria-label="Your name"
          maxLength={32}
          value={draft ?? name}
          onFocus={() => setDraft(name)}
          onChange={(event) => setDraft(event.target.value)}
          onBlur={commitName}
          onKeyDown={(event) => {
            if (event.key === "Enter") {
              event.currentTarget.blur();
            }
          }}
        />

        <button type="button" onClick={() => void share()}>
          {copied ? "Copied!" : "Share"}
        </button>
        <button type="button" onClick={newDoc}>
          New doc
        </button>
      </div>
    </header>
  );
}
