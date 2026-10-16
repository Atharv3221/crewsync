// @vitest-environment jsdom
import { act, cleanup, fireEvent, render } from "@testing-library/react";
import { StrictMode, useEffect } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useCollab } from "./useCollab";

/** Records every socket the hook opens; tests accept, feed and inspect them. */
class FakeSocket {
  static readonly CONNECTING = 0;
  static readonly OPEN = 1;
  static readonly CLOSED = 3;
  static instances: FakeSocket[] = [];

  readonly url: string;
  readonly sent: Record<string, unknown>[] = [];
  readyState = FakeSocket.CONNECTING;
  closeCalls = 0;
  onopen: (() => void) | null = null;
  onmessage: ((event: { data: string }) => void) | null = null;
  onclose: ((event: { code: number }) => void) | null = null;

  constructor(url: string) {
    this.url = url;
    FakeSocket.instances.push(this);
  }

  send(data: string): void {
    this.sent.push(JSON.parse(data) as Record<string, unknown>);
  }

  close(): void {
    this.closeCalls++;
    this.readyState = FakeSocket.CLOSED;
  }

  /** The server accepts the handshake. */
  accept(): void {
    this.readyState = FakeSocket.OPEN;
    this.onopen?.();
  }

  receive(message: unknown): void {
    this.onmessage?.({ data: JSON.stringify(message) });
  }

  sentOfType(type: string): Record<string, unknown>[] {
    return this.sent.filter((message) => message.type === type);
  }
}

interface Props {
  readonly docId: string;
  readonly userId: string;
  readonly name: string;
  readonly token: string;
  readonly options?: { readonly theme: string };
}

type Collab = Omit<ReturnType<typeof useCollab>, "textareaRef">;

let latest: Collab;

function Harness({ docId, userId, name, token }: Props) {
  const { textareaRef, handlers, ...snapshot } = useCollab(
    docId,
    userId,
    name,
    token,
  );
  useEffect(() => {
    latest = { handlers, ...snapshot };
  });
  return (
    <textarea
      data-testid="editor"
      ref={textareaRef}
      onInput={handlers.onInput}
      onSelect={handlers.onSelect}
    />
  );
}

const BASE: Props = {
  docId: "doc-1",
  userId: "me",
  name: "Ada",
  token: "jwt-1",
};

function mount(props: Props = BASE) {
  const view = render(<Harness {...props} />, { wrapper: StrictMode });
  return {
    ...view,
    update: (next: Props) => view.rerender(<Harness {...next} />),
    textarea: view.getByTestId("editor") as HTMLTextAreaElement,
  };
}

/** Lets the server accept every pending socket and returns the ones that stayed open. */
function acceptAll(): FakeSocket[] {
  act(() =>
    FakeSocket.instances
      .filter((socket) => socket.readyState === FakeSocket.CONNECTING)
      .forEach((socket) => socket.accept()),
  );
  return FakeSocket.instances.filter(
    (socket) => socket.readyState === FakeSocket.OPEN,
  );
}

/** The id of the character an insert message created. */
function charIdOf(message: Record<string, unknown>): {
  counter: number;
  site: string;
} {
  const op = message.op as { counter: number; site: string };
  return { counter: op.counter, site: op.site };
}

function type(textarea: HTMLTextAreaElement, value: string): void {
  act(() => {
    textarea.value = value;
    textarea.setSelectionRange(value.length, value.length);
    fireEvent.input(textarea);
  });
}

describe("useCollab", () => {
  beforeEach(() => {
    FakeSocket.instances = [];
    vi.stubGlobal("WebSocket", FakeSocket);
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("leaves exactly one live socket after StrictMode's double mount", () => {
    mount();

    const live = acceptAll();

    expect(live).toHaveLength(1);
    expect(live[0].url).toContain("/ws/doc-1?token=jwt-1");
    const discarded = FakeSocket.instances.filter(
      (socket) => socket !== live[0],
    );
    discarded.forEach((socket) => expect(socket.sent).toEqual([]));
    expect(live[0].sentOfType("hello")).toHaveLength(1);
  });

  it("keeps the same client and socket when re-rendered with equal but new props", () => {
    const view = mount();
    const [live] = acceptAll();
    const handlers = latest.handlers;
    const created = FakeSocket.instances.length;

    view.update({ ...BASE, options: { theme: "dark" } });
    view.update({ ...BASE });

    expect(FakeSocket.instances).toHaveLength(created);
    expect(live.closeCalls).toBe(0);
    expect(latest.handlers).toBe(handlers);
    expect(latest.status).toBe("online");
  });

  it("sends a name change as presence without reconnecting", () => {
    const view = mount();
    const [live] = acceptAll();
    const created = FakeSocket.instances.length;

    view.update({ ...BASE, name: "Ada Lovelace" });

    expect(FakeSocket.instances).toHaveLength(created);
    expect(live.closeCalls).toBe(0);
    expect(live.sentOfType("presence").at(-1)).toMatchObject({
      name: "Ada Lovelace",
    });
  });

  it("reconnects when the doc changes", () => {
    const view = mount();
    const [first] = acceptAll();

    view.update({ ...BASE, docId: "doc-2" });

    expect(first.closeCalls).toBe(1);
    const live = acceptAll();
    expect(live).toHaveLength(1);
    expect(live[0].url).toContain("/ws/doc-2?");
  });

  it("closes the socket on unmount", () => {
    const view = mount();
    const [live] = acceptAll();

    view.unmount();

    expect(live.closeCalls).toBe(1);
  });

  it("types newlines without throwing or reconnecting", () => {
    const view = mount();
    const [live] = acceptAll();
    act(() => live.receive({ type: "init", seq: 0, ops: [] }));
    const created = FakeSocket.instances.length;

    type(view.textarea, "a");
    type(view.textarea, "a\n");
    type(view.textarea, "a\n\n");

    const inserted = live
      .sentOfType("op")
      .map((message) => message.op as { value: string });
    expect(inserted.map((op) => op.value)).toEqual(["a", "\n", "\n"]);
    expect(FakeSocket.instances).toHaveLength(created);
    expect(live.closeCalls).toBe(0);
    expect(latest.length).toBe(3);
  });

  it("places peer cursors on newlines and after a trailing newline", () => {
    const view = mount();
    const [live] = acceptAll();
    act(() => live.receive({ type: "init", seq: 0, ops: [] }));
    type(view.textarea, "ab\n\n");
    const ops = live.sentOfType("op").map((message) => charIdOf(message));
    const peerAt = (cursor: unknown) =>
      act(() =>
        live.receive({
          type: "presence",
          site: "s2",
          userId: "grace",
          name: "Grace",
          cursor,
        }),
      );

    peerAt(null);
    expect(latest.peers[0].line).toBe(1);
    peerAt(ops[2]);
    expect(latest.peers[0].line).toBe(2);
    peerAt(ops[3]);
    expect(latest.peers[0].line).toBe(3);
    peerAt({ counter: 999, site: "unknown" });
    expect(latest.peers[0].line).toBe(3);
  });

  it("shows a remote newline in the textarea", () => {
    const view = mount();
    const [live] = acceptAll();
    act(() => live.receive({ type: "init", seq: 0, ops: [] }));
    type(view.textarea, "a");
    const first = charIdOf(live.sentOfType("op")[0]);

    act(() =>
      live.receive({
        type: "op",
        seq: 2,
        op: {
          kind: "insert",
          counter: 5,
          site: "s2",
          origin: first,
          value: "\n",
        },
      }),
    );

    expect(view.textarea.value).toBe("a\n");
    expect(latest.length).toBe(2);
  });
});
