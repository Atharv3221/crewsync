import { useCallback, useRef, useState } from "react";
import type { User } from "../lib/api";
import { routeHash } from "../lib/routes";
import { useDismiss } from "./useDismiss";
import "./UserMenu.css";

interface UserMenuProps {
  readonly user: User;
  readonly onRename: (name: string) => Promise<void>;
  readonly onLogout: () => void;
}

const MAX_NAME_LENGTH = 100;

function initial(name: string): string {
  return name.trim().charAt(0).toUpperCase() || "?";
}

/** The signed-in user's avatar button with a menu: display name, my docs and log out. */
export function UserMenu({ user, onRename, onLogout }: UserMenuProps) {
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const ref = useRef<HTMLDivElement>(null);
  const close = useCallback(() => setOpen(false), []);
  useDismiss(ref, open, close);

  const commitName = (): void => {
    const name = draft?.trim() ?? "";
    if (name !== "" && name !== user.name) {
      onRename(name).then(
        () => setError(null),
        () => setError("Couldn't save your name"),
      );
    }
    setDraft(null);
  };

  return (
    <div className="user-menu" ref={ref}>
      <button
        type="button"
        className="user-menu-button"
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen(!open)}
      >
        <span className="avatar" aria-hidden="true">
          {initial(user.name)}
        </span>
        <span className="user-menu-name">{user.name}</span>
      </button>

      {open && (
        <div className="user-menu-panel" role="menu">
          <label className="field-label" htmlFor="display-name">
            Display name
          </label>
          <input
            id="display-name"
            className="text-input"
            maxLength={MAX_NAME_LENGTH}
            value={draft ?? user.name}
            onFocus={() => setDraft(user.name)}
            onChange={(event) => setDraft(event.target.value)}
            onBlur={commitName}
            onKeyDown={(event) => {
              if (event.key === "Enter") {
                event.currentTarget.blur();
              }
            }}
          />
          {error !== null && <p className="field-error">{error}</p>}
          <p className="user-menu-email">{user.email}</p>
          <a
            className="user-menu-item"
            href={routeHash({ page: "home" })}
            onClick={close}
          >
            My docs
          </a>
          <button type="button" className="user-menu-item" onClick={onLogout}>
            Log out
          </button>
        </div>
      )}
    </div>
  );
}
