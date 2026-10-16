import { useCallback, useEffect, useMemo, useRef, useState } from "react";
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
  accessLost: null,
  peers: [],
  cursors: [],
  unsynced: 0,
  length: 0,
  text: "",
  title: null,
  titleVersion: -1,
};

/**
 * React custom hook that manages real-time document collaboration, network client lifecycle,
 * textarea binding, and state synchronization.
 *
 * @param docId - The unique identifier of the document being edited.
 * @param userId - The unique identifier of the current user.
 * @param name - The display name of the current user. Changes are sent as presence; they never reconnect.
 * @param token - The session token for the WebSocket handshake.
 * @returns An object containing the `textareaRef`, memoized event `handlers` for the textarea, and the live `snapshot` fields.
 */
export function useCollab(
  docId: string,
  userId: string,
  name: string,
  token: string,
) {
  const [snapshot, setSnapshot] = useState<CollabSnapshot>(INITIAL);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const clientRef = useRef<CollabClient | null>(null);
  const nameRef = useRef(name);

  // Keep the latest name without recreating the connection.
  useEffect(() => {
    nameRef.current = name;
    clientRef.current?.setName(name);
  }, [name]);

  // One client per document and session. Only primitives in the deps: an object, callback or the
  // name here would tear the socket down on every re-render.
  useEffect(() => {
    const client = new CollabClient({
      docId,
      userId,
      name: nameRef.current,
      token,
      onChange: setSnapshot,
    });
    clientRef.current = client;
    client.attach(textareaRef.current);
    client.start();
    return () => {
      client.dispose();
      clientRef.current = null;
    };
  }, [docId, userId, token]);

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

  const setTitle = useCallback(
    (title: string, version: number) =>
      clientRef.current?.setTitle(title, version),
    [],
  );

  return { textareaRef, handlers, setTitle, ...snapshot };
}
