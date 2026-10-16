import { useEffect, useState } from "react";
import { listMyDocs, type Doc, type DocRole, type User } from "../lib/api";
import { routeHash } from "../lib/routes";
import { UserMenu } from "./UserMenu";
import "./TopBar.css";
import "./HomePage.css";

interface HomePageProps {
  readonly user: User;
  readonly onRename: (name: string) => Promise<void>;
  readonly onLogout: () => void;
  readonly onNewDoc: () => Promise<void>;
}

type Docs =
  | { readonly state: "loading" | "failed" }
  | { readonly state: "ready"; readonly docs: readonly Doc[] };

const ROLE_LABEL: Record<DocRole, string> = {
  OWNER: "Owner",
  EDITOR: "Editor",
};

const DATE_FORMAT = new Intl.DateTimeFormat(undefined, { dateStyle: "medium" });

/** My docs, newest first, with a button to start a new one. */
export function HomePage({
  user,
  onRename,
  onLogout,
  onNewDoc,
}: HomePageProps) {
  const [docs, setDocs] = useState<Docs>({ state: "loading" });
  const [creating, setCreating] = useState(false);

  useEffect(() => {
    let active = true;
    listMyDocs().then(
      (loaded) => active && setDocs({ state: "ready", docs: loaded }),
      () => active && setDocs({ state: "failed" }),
    );
    return () => {
      active = false;
    };
  }, []);

  const newDoc = (): void => {
    setCreating(true);
    onNewDoc().finally(() => setCreating(false));
  };

  const newDocButton = (
    <button
      type="button"
      className="button button-primary"
      disabled={creating}
      onClick={newDoc}
    >
      {creating ? "Creating…" : "New doc"}
    </button>
  );

  return (
    <div className="home">
      <header className="topbar">
        <div className="topbar-left">
          <span className="logo">CrewSync</span>
        </div>
        <div className="topbar-right">
          <UserMenu user={user} onRename={onRename} onLogout={onLogout} />
        </div>
      </header>

      <main className="home-main">
        <div className="home-heading">
          <h1 className="home-title">My docs</h1>
          {docs.state === "ready" && docs.docs.length > 0 && newDocButton}
        </div>

        {docs.state === "loading" && <p className="home-hint">Loading…</p>}
        {docs.state === "failed" && (
          <p className="home-hint">Can't reach the server.</p>
        )}
        {docs.state === "ready" && docs.docs.length === 0 && (
          <div className="home-empty">
            <h2 className="home-empty-title">No docs yet</h2>
            <p className="home-hint">
              Start one, then invite people from the Share menu.
            </p>
            {newDocButton}
          </div>
        )}
        {docs.state === "ready" && docs.docs.length > 0 && (
          <ul className="doc-list">
            {docs.docs.map((doc) => (
              <li key={doc.id}>
                <a
                  className="doc-row"
                  href={routeHash({ page: "doc", docId: doc.id })}
                >
                  <span className="doc-row-title">{doc.title}</span>
                  <span className="doc-row-role">{ROLE_LABEL[doc.role]}</span>
                  <time className="doc-row-date" dateTime={doc.createdAt}>
                    {DATE_FORMAT.format(new Date(doc.createdAt))}
                  </time>
                </a>
              </li>
            ))}
          </ul>
        )}
      </main>
    </div>
  );
}
