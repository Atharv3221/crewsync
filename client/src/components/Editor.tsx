import { useLayoutEffect, useRef, type RefObject } from "react";
import type {
  CollabHandlers,
  CollabSnapshot,
  ConnectionStatus,
} from "../collab";
import { RemoteCursors } from "./RemoteCursors";
import "./Editor.css";

interface EditorProps extends Pick<
  CollabSnapshot,
  "length" | "unsynced" | "status" | "text" | "cursors"
> {
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
  text,
  cursors,
}: EditorProps) {
  const overlayRef = useRef<HTMLDivElement>(null);

  // The overlay can't scroll on its own; keep it aligned with the textarea.
  const syncScroll = (): void => {
    if (overlayRef.current !== null && textareaRef.current !== null) {
      overlayRef.current.scrollTop = textareaRef.current.scrollTop;
    }
  };
  useLayoutEffect(syncScroll);

  return (
    <main className="editor">
      <div className="editor-sheet">
        <textarea
          ref={textareaRef}
          className="editor-text editor-metrics"
          aria-label="Document text"
          placeholder="Start typing… then share the link to edit together."
          spellCheck={false}
          autoFocus
          onInput={handlers.onInput}
          onSelect={handlers.onSelect}
          onCompositionStart={handlers.onCompositionStart}
          onCompositionEnd={handlers.onCompositionEnd}
          onScroll={syncScroll}
        />
        <RemoteCursors ref={overlayRef} text={text} cursors={cursors} />
      </div>
      <footer className="editor-status">
        <span>{length} characters</span>
        <span className={unsynced > 0 ? "pending" : undefined}>
          {syncLabel(unsynced, status)}
        </span>
      </footer>
    </main>
  );
}
