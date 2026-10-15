import { useState } from "react";
import { useCollab } from "../collab";
import { getUserId, getUserName, setUserName } from "../lib/identity";
import { Editor } from "./Editor";
import { TopBar } from "./TopBar";

interface DocPageProps {
  readonly docId: string;
}

/** One open document: owns the collab client and passes its state to the UI. */
export function DocPage({ docId }: DocPageProps) {
  const [userId] = useState(getUserId);
  const [name, setName] = useState(getUserName);
  const collab = useCollab(docId, userId, name);

  const rename = (next: string): void => {
    setUserName(next);
    setName(getUserName());
  };

  return (
    <div className="app">
      <TopBar
        docId={docId}
        name={name}
        onRename={rename}
        peers={collab.peers}
        status={collab.status}
      />
      <Editor
        textareaRef={collab.textareaRef}
        handlers={collab.handlers}
        length={collab.length}
        unsynced={collab.unsynced}
        status={collab.status}
      />
    </div>
  );
}
