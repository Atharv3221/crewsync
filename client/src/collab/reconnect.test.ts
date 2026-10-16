import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { CollabClient, type CollabSnapshot } from "./CollabClient";

const DOC_ID = "3f9a0c7b-e2d4-41a6-8b1c-0d2e3f4a5b6c";
const FIRST_RETRY_MS = 500;

/** Stands in for the browser WebSocket; tests drive its lifecycle by hand. */
class FakeSocket {
  static readonly CONNECTING = 0;
  static readonly OPEN = 1;
  static instances: FakeSocket[] = [];

  readonly url: string;
  readyState = 0;
  onopen: (() => void) | null = null;
  onmessage: ((event: { data: string }) => void) | null = null;
  onclose: ((event: { code: number }) => void) | null = null;

  constructor(url: string) {
    this.url = url;
    FakeSocket.instances.push(this);
  }

  send(): void {}

  close(): void {
    this.readyState = 3;
  }

  open(): void {
    this.readyState = FakeSocket.OPEN;
    this.onopen?.();
  }

  drop(code = 1006): void {
    this.readyState = 3;
    this.onclose?.({ code });
  }

  receive(message: unknown): void {
    this.onmessage?.({ data: JSON.stringify(message) });
  }
}

function respond(status: number, body?: unknown): Response {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
  });
}

const DOC = {
  id: DOC_ID,
  title: "Plans",
  titleVersion: 0,
  role: "EDITOR",
  createdAt: "2026-10-16T00:00:00Z",
};

describe("CollabClient reconnect access check", () => {
  let storage: Map<string, string>;
  let location: { protocol: string; host: string; hash: string };
  let fetchMock: ReturnType<typeof vi.fn>;
  let snapshots: CollabSnapshot[];
  let client: CollabClient;

  const latest = (): CollabSnapshot => snapshots[snapshots.length - 1];

  /** Opens the first socket, then drops it so the client starts backing off. */
  function connectThenDrop(code?: number): void {
    client.start();
    FakeSocket.instances[0].open();
    FakeSocket.instances[0].drop(code);
  }

  beforeEach(() => {
    vi.useFakeTimers();
    FakeSocket.instances = [];
    storage = new Map([["crewsync.token", "jwt-1"]]);
    vi.stubGlobal("localStorage", {
      getItem: (key: string) => storage.get(key) ?? null,
      setItem: (key: string, value: string) => storage.set(key, value),
      removeItem: (key: string) => storage.delete(key),
    });
    location = {
      protocol: "http:",
      host: "localhost:5173",
      hash: `#${DOC_ID}`,
    };
    vi.stubGlobal("location", location);
    vi.stubGlobal("window", { location });
    vi.stubGlobal("WebSocket", FakeSocket);
    fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    snapshots = [];
    client = new CollabClient({
      docId: DOC_ID,
      userId: "me",
      name: "Me",
      token: "jwt-1",
      onChange: (snapshot) => snapshots.push(snapshot),
    });
  });

  afterEach(() => {
    client.dispose();
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it("connects with the token in the URL", () => {
    client.start();

    expect(FakeSocket.instances[0].url).toBe(
      `ws://localhost:5173/ws/${DOC_ID}?token=jwt-1`,
    );
  });

  it("checks the doc with the API before reconnecting, then reconnects", async () => {
    fetchMock.mockResolvedValue(respond(200, DOC));
    connectThenDrop();

    await vi.advanceTimersByTimeAsync(FIRST_RETRY_MS);

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(path).toBe(`/api/docs/${DOC_ID}`);
    expect((init.headers as Record<string, string>).Authorization).toBe(
      "Bearer jwt-1",
    );
    expect(FakeSocket.instances).toHaveLength(2);
    expect(latest().accessLost).toBeNull();
  });

  it("on 401 clears the token, goes to login remembering the doc, and stops", async () => {
    fetchMock.mockResolvedValue(
      respond(401, { status: 401, message: "Authentication required" }),
    );
    connectThenDrop();

    await vi.advanceTimersByTimeAsync(FIRST_RETRY_MS);
    await vi.advanceTimersByTimeAsync(60_000);

    expect(storage.has("crewsync.token")).toBe(false);
    expect(location.hash).toBe(`#/login?next=${DOC_ID}`);
    expect(latest().accessLost).toBe("unauthorized");
    expect(FakeSocket.instances).toHaveLength(1);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("on 403 stops reconnecting and reports lost access", async () => {
    fetchMock.mockResolvedValue(
      respond(403, { status: 403, message: "No access" }),
    );
    connectThenDrop();

    await vi.advanceTimersByTimeAsync(FIRST_RETRY_MS);
    await vi.advanceTimersByTimeAsync(60_000);

    expect(latest().accessLost).toBe("forbidden");
    expect(FakeSocket.instances).toHaveLength(1);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(storage.get("crewsync.token")).toBe("jwt-1");
  });

  it("on 404 stops reconnecting and reports the doc missing", async () => {
    fetchMock.mockResolvedValue(
      respond(404, { status: 404, message: "Document not found" }),
    );
    connectThenDrop();

    await vi.advanceTimersByTimeAsync(FIRST_RETRY_MS);
    await vi.advanceTimersByTimeAsync(60_000);

    expect(latest().accessLost).toBe("missing");
    expect(FakeSocket.instances).toHaveLength(1);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("on a network error keeps backing off and checks again", async () => {
    fetchMock
      .mockRejectedValueOnce(new TypeError("Failed to fetch"))
      .mockResolvedValueOnce(respond(503))
      .mockResolvedValue(respond(200, DOC));
    connectThenDrop();

    await vi.advanceTimersByTimeAsync(FIRST_RETRY_MS);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(FakeSocket.instances).toHaveLength(1);
    expect(latest().status).toBe("offline");

    await vi.advanceTimersByTimeAsync(2 * FIRST_RETRY_MS);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(FakeSocket.instances).toHaveLength(1);

    await vi.advanceTimersByTimeAsync(4 * FIRST_RETRY_MS);
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(FakeSocket.instances).toHaveLength(2);
    expect(latest().accessLost).toBeNull();
  });

  it("treats close code 4403 like a 403 without asking the API", async () => {
    connectThenDrop(4403);

    await vi.advanceTimersByTimeAsync(60_000);

    expect(latest().accessLost).toBe("forbidden");
    expect(fetchMock).not.toHaveBeenCalled();
    expect(FakeSocket.instances).toHaveLength(1);
  });

  it("drops a peer's presence when the server says they left", () => {
    client.start();
    const socket = FakeSocket.instances[0];
    socket.open();
    const cursor = null;
    socket.receive({
      type: "presence",
      site: "s2",
      userId: "grace",
      name: "Grace",
      cursor,
    });
    socket.receive({
      type: "presence",
      site: "s3",
      userId: "grace",
      name: "Grace",
      cursor,
    });
    expect(latest().peers.map((peer) => peer.userId)).toEqual(["grace"]);

    socket.receive({ type: "leave", userId: "grace" });

    expect(latest().peers).toEqual([]);
  });

  it("shows the title from a rename broadcast and from a local save", () => {
    client.start();
    const socket = FakeSocket.instances[0];
    socket.open();
    expect(latest().title).toBeNull();

    socket.receive({ type: "title", title: "Launch plan", version: 1 });
    expect(latest()).toMatchObject({ title: "Launch plan", titleVersion: 1 });

    client.setTitle("Roadmap", 2);
    expect(latest()).toMatchObject({ title: "Roadmap", titleVersion: 2 });
  });

  it("keeps the newest title when saves and broadcasts arrive out of order", () => {
    client.start();
    const socket = FakeSocket.instances[0];
    socket.open();

    // I saved "Mine" (v1) while someone else saved "Theirs" (v2). Their broadcast beats my reply.
    socket.receive({ type: "title", title: "Mine", version: 1 });
    socket.receive({ type: "title", title: "Theirs", version: 2 });
    client.setTitle("Mine", 1);
    socket.receive({ type: "title", title: "Mine", version: 1 });

    expect(latest()).toMatchObject({ title: "Theirs", titleVersion: 2 });
  });

  it("picks up a rename that happened while offline from the access check", async () => {
    fetchMock.mockResolvedValue(
      respond(200, { ...DOC, title: "Renamed", titleVersion: 4 }),
    );
    connectThenDrop();

    await vi.advanceTimersByTimeAsync(FIRST_RETRY_MS);

    expect(latest().title).toBe("Renamed");
  });
});
