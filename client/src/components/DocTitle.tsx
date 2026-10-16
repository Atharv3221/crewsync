import { useState } from "react";

interface DocTitleProps {
  readonly title: string;
  /** Only the owner may rename; everyone else sees the title read-only. */
  readonly editable: boolean;
  readonly onRename: (title: string) => Promise<void>;
}

const MAX_TITLE_LENGTH = 200;

/**
 * The doc's title as an inline field: Enter or blur saves, Escape cancels. A draft exists only once
 * the user types, so an untouched field keeps showing renames by others and never saves on blur.
 */
export function DocTitle({ title, editable, onRename }: DocTitleProps) {
  const [draft, setDraft] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  if (!editable) {
    return (
      <span
        className="doc-title doc-title-readonly"
        title={`${title} · only the owner can rename this doc`}
      >
        {title}
      </span>
    );
  }

  const commit = (): void => {
    const next = draft?.trim() ?? "";
    if (next !== "" && next !== title) {
      onRename(next).then(
        () => setError(null),
        () => setError("Couldn't rename the doc"),
      );
    }
    setDraft(null);
  };

  return (
    <input
      className={`doc-title${error !== null ? " doc-title-error" : ""}`}
      aria-label="Document title"
      title={error ?? "Rename this doc"}
      maxLength={MAX_TITLE_LENGTH}
      value={draft ?? title}
      onChange={(event) => setDraft(event.target.value)}
      onBlur={commit}
      onKeyDown={(event) => {
        if (event.key === "Enter") {
          event.currentTarget.blur();
        } else if (event.key === "Escape") {
          setDraft(null);
          event.currentTarget.blur();
        }
      }}
    />
  );
}
