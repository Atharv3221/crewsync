import type { RefObject } from "react";
import type { CollabHandlers, CollabSnapshot, ConnectionStatus } from "../collab";
import "./Editor.css";

interface EditorProps
  extends Pick<CollabSnapshot, "length" | "unsynced" | "status"> {
  readonly textareaRef: RefObject<HTMLTextAreaElement | null>;
  readonly handlers: CollabHandlers;
}

function syncLabel(unsynced: number, status: ConnectionStatus): string {
  let label = "All changes synced";
  if (unsynced > 0) {
    label = `${unsynced} change${unsynced === 1 ? "" : "s"} waiting to sync`;
  } else if (status !== "online") {
    label = "Not connected";
  }
  return label;
}

/** The shared text area. Uncontrolled: TextareaBinding writes its value, React never does. */
export function Editor({
  textareaRef,
  handlers,
  length,
  unsynced,
  status,
}: EditorProps) {
  return (
    <main className="editor">
      <textarea
        ref={textareaRef}
        className="editor-text"
        aria-label="Document text"
        placeholder="Start typing… then share the link to edit together."
        spellCheck={false}
        autoFocus
        onInput={handlers.onInput}
        onSelect={handlers.onSelect}
        onCompositionStart={handlers.onCompositionStart}
        onCompositionEnd={handlers.onCompositionEnd}
      />
      <footer className="editor-status">
        <span>{length} characters</span>
        <span className={unsynced > 0 ? "pending" : undefined}>
          {syncLabel(unsynced, status)}
        </span>
      </footer>
    </main>
  );
}
