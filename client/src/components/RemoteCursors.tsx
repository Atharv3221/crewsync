import { forwardRef, type CSSProperties, type ReactNode } from "react";
import type { RemoteCursor } from "../collab";

interface RemoteCursorsProps {
  readonly text: string;
  readonly cursors: readonly RemoteCursor[];
}

/**
 * Other people's carets. A transparent copy of the text laid out exactly like the textarea, with
 * a zero-width marker at each caret: the browser places them, so wrapping and tabs line up for free.
 */
export const RemoteCursors = forwardRef<HTMLDivElement, RemoteCursorsProps>(
  function RemoteCursors({ text, cursors }, ref) {
    const parts: ReactNode[] = [];
    let start = 0;
    for (const cursor of cursors) {
      const pos = Math.min(cursor.pos, text.length);
      parts.push(text.slice(start, pos));
      parts.push(
        <span
          key={cursor.site}
          className="remote-caret"
          style={{ "--caret": cursor.color } as CSSProperties}
          data-testid="remote-caret"
        >
          <span className="remote-caret-label">{cursor.name}</span>
        </span>,
      );
      start = pos;
    }
    parts.push(text.slice(start));

    return (
      <div
        ref={ref}
        className="remote-cursors editor-metrics"
        aria-hidden="true"
      >
        {parts}
      </div>
    );
  },
);
