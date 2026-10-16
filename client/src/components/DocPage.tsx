import { useCallback, useEffect, useState } from "react";
import { useCollab } from "../collab";
import { ApiError, getDoc, renameDoc, type Doc, type User } from "../lib/api";
import { getToken } from "../lib/auth";
import { navigate } from "../lib/routes";
import { Editor } from "./Editor";
import { Notice } from "./Notice";
import { TopBar } from "./TopBar";

interface DocPageProps {
  readonly docId: string;
  readonly user: User;
  readonly onRename: (name: string) => Promise<void>;
  readonly onLogout: () => void;
  readonly onNewDoc: () => Promise<void>;
}

type Access =
  | { readonly state: "checking" | "forbidden" | "missing" | "failed" }
  | { readonly state: "allowed"; readonly doc: Doc };

function accessFor(error: unknown): Access {
  let state: "checking" | "forbidden" | "missing" | "failed" = "failed";
  if (error instanceof ApiError) {
    if (error.status === 403) {
      state = "forbidden";
    } else if (error.status === 404 || error.status === 400) {
      state = "missing";
    } else if (error.status === 401) {
      state = "checking"; // the api layer is already sending the user to the login page
    }
  }
  return { state };
}

const backToMyDocs = (): void => navigate({ page: "home" });

/** One document. Checks access with the API before opening the WebSocket. */
export function DocPage(props: DocPageProps) {
  const { docId } = props;
  const [access, setAccess] = useState<Access>({ state: "checking" });

  useEffect(() => {
    let active = true;
    getDoc(docId).then(
      (doc) => active && setAccess({ state: "allowed", doc }),
      (error: unknown) => active && setAccess(accessFor(error)),
    );
    return () => {
      active = false;
    };
  }, [docId]);

  let page;
  switch (access.state) {
    case "allowed":
      page = <OpenDoc {...props} doc={access.doc} />;
      break;
    case "forbidden":
      page = (
        <Notice
          title="You don't have access to this doc"
          detail="Ask the owner to invite you."
          action="Back to my docs"
          onAction={backToMyDocs}
        />
      );
      break;
    case "missing":
      page = (
        <Notice
          title="Doc not found"
          action="Back to my docs"
          onAction={backToMyDocs}
        />
      );
      break;
    case "failed":
      page = (
        <Notice
          title="Can't reach the server"
          action="Retry"
          onAction={() => location.reload()}
        />
      );
      break;
    case "checking":
      page = <Notice title="Opening document…" />;
      break;
  }
  return page;
}

/** An open document: owns the collab client and passes its state to the UI. */
function OpenDoc({
  doc,
  user,
  onRename,
  onLogout,
  onNewDoc,
}: DocPageProps & { readonly doc: Doc }) {
  const collab = useCollab(doc.id, user.id, user.name, getToken() ?? "");
  // The doc was fetched on open; the client hears every rename after that. Newest version wins.
  const title =
    collab.title !== null && collab.titleVersion > doc.titleVersion
      ? collab.title
      : doc.title;
  const { setTitle } = collab;

  const onRenameDoc = useCallback(
    async (next: string) => {
      const renamed = await renameDoc(doc.id, next);
      setTitle(renamed.title, renamed.titleVersion);
    },
    [doc.id, setTitle],
  );

  useEffect(() => {
    const previous = document.title;
    document.title = `${title} · CrewSync`;
    return () => {
      document.title = previous;
    };
  }, [title]);

  let page;
  if (collab.accessLost === "forbidden") {
    page = (
      <Notice
        title="You no longer have access to this doc"
        detail="Ask the owner to invite you again."
        action="Back to my docs"
        onAction={backToMyDocs}
      />
    );
  } else if (collab.accessLost === "missing") {
    page = (
      <Notice
        title="Doc not found"
        action="Back to my docs"
        onAction={backToMyDocs}
      />
    );
  } else if (collab.accessLost === "unauthorized") {
    page = (
      <Notice
        title="Your session has expired"
        detail="Taking you to the login page…"
      />
    );
  } else {
    page = (
      <div className="app">
        <TopBar
          doc={doc}
          user={user}
          title={title}
          onRenameDoc={onRenameDoc}
          onRename={onRename}
          onLogout={onLogout}
          onNewDoc={onNewDoc}
          peers={collab.peers}
          status={collab.status}
        />
        <Editor
          textareaRef={collab.textareaRef}
          handlers={collab.handlers}
          length={collab.length}
          unsynced={collab.unsynced}
          status={collab.status}
          text={collab.text}
          cursors={collab.cursors}
        />
      </div>
    );
  }
  return page;
}
