import { useEffect, useMemo, useRef, useState } from "react";
import { CollabClient, type CollabSnapshot } from "./CollabClient";

/** Stable event handlers to spread onto the collaborative textarea. */
export interface CollabHandlers {
  readonly onInput: () => void;
  readonly onSelect: () => void;
  readonly onCompositionStart: () => void;
  readonly onCompositionEnd: () => void;
}

/** Initial default snapshot representing a connecting collaboration state. */
const INITIAL: CollabSnapshot = {
  status: "connecting",
  peers: [],
  unsynced: 0,
  length: 0,
};

/**
 * React custom hook that manages real-time document collaboration, network client lifecycle,
 * textarea binding, and state synchronization.
 *
 * @param docId - The unique identifier of the document being edited.
 * @param userId - The unique identifier of the current user.
 * @param name - The display name of the current user.
 * @returns An object containing the `textareaRef`, memoized event `handlers` for the textarea, and the live `snapshot` fields.
 */
export function useCollab(docId: string, userId: string, name: string) {
  const [snapshot, setSnapshot] = useState<CollabSnapshot>(INITIAL);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const clientRef = useRef<CollabClient | null>(null);
  const nameRef = useRef(name);

  // Keep the latest name without recreating the connection.
  useEffect(() => {
    nameRef.current = name;
    clientRef.current?.setName(name);
  }, [name]);

  // One client per document; torn down when the doc changes or the page closes.
  useEffect(() => {
    const client = new CollabClient({
      docId,
      userId,
      name: nameRef.current,
      onChange: setSnapshot,
    });
    clientRef.current = client;
    client.attach(textareaRef.current);
    client.start();
    return () => {
      client.dispose();
      clientRef.current = null;
    };
  }, [docId, userId]);

  // Stable handlers for the textarea.
  const handlers = useMemo<CollabHandlers>(
    () => ({
      onInput: () => clientRef.current?.handleInput(),
      onSelect: () => clientRef.current?.handleSelect(),
      onCompositionStart: () => clientRef.current?.handleCompositionStart(),
      onCompositionEnd: () => clientRef.current?.handleCompositionEnd(),
    }),
    [],
  );

  return { textareaRef, handlers, ...snapshot };
}
