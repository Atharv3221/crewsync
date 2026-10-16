import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, createDoc, getMe, login, removeMember } from "./api.ts";
import { getToken, setToken } from "./auth.ts";

const DOC_ID = "3f9a0c7b-e2d4-41a6-8b1c-0d2e3f4a5b6c";

function respond(status: number, body?: unknown): Response {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
  });
}

function sentHeaders(
  fetchMock: ReturnType<typeof vi.fn>,
): Record<string, string> {
  const init = fetchMock.mock.calls[0][1] as RequestInit;
  return init.headers as Record<string, string>;
}

describe("api", () => {
  let storage: Map<string, string>;
  let fetchMock: ReturnType<typeof vi.fn>;
  let location: { hash: string };

  beforeEach(() => {
    storage = new Map();
    vi.stubGlobal("localStorage", {
      getItem: (key: string) => storage.get(key) ?? null,
      setItem: (key: string, value: string) => storage.set(key, value),
      removeItem: (key: string) => storage.delete(key),
    });
    location = { hash: `#${DOC_ID}` };
    vi.stubGlobal("window", { location });
    fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => vi.unstubAllGlobals());

  it("attaches the bearer token", async () => {
    setToken("jwt-123");
    fetchMock.mockResolvedValue(
      respond(200, { id: "u1", name: "Ada", email: "a@b.co" }),
    );

    await getMe();

    expect(fetchMock).toHaveBeenCalledWith("/api/me", expect.anything());
    expect(sentHeaders(fetchMock).Authorization).toBe("Bearer jwt-123");
  });

  it("sends no Authorization header without a token", async () => {
    fetchMock.mockResolvedValue(respond(200, { token: "t", user: {} }));

    await login("a@b.co", "password1");

    expect(sentHeaders(fetchMock).Authorization).toBeUndefined();
    expect(sentHeaders(fetchMock)["Content-Type"]).toBe("application/json");
  });

  it("clears the token and goes to login on 401, remembering the doc", async () => {
    setToken("expired");
    fetchMock.mockResolvedValue(
      respond(401, { status: 401, message: "Authentication required" }),
    );

    await expect(getMe()).rejects.toMatchObject({ status: 401 });

    expect(getToken()).toBeNull();
    expect(location.hash).toBe(`#/login?next=${DOC_ID}`);
  });

  it("keeps the user on the login page when login itself fails", async () => {
    location.hash = "#/login";
    fetchMock.mockResolvedValue(
      respond(401, { status: 401, message: "Invalid email or password" }),
    );

    const failure = login("a@b.co", "wrong-password");

    await expect(failure).rejects.toEqual(
      new ApiError(401, "Invalid email or password"),
    );
    expect(location.hash).toBe("#/login");
  });

  it("surfaces the server's error message", async () => {
    setToken("t");
    fetchMock.mockResolvedValue(
      respond(409, { status: 409, message: "Grace is already a member" }),
    );

    await expect(createDoc()).rejects.toMatchObject({
      status: 409,
      message: "Grace is already a member",
    });
    expect(getToken()).toBe("t");
  });

  it("falls back to a generic message for non-JSON errors", async () => {
    fetchMock.mockResolvedValue(new Response("oops", { status: 502 }));

    await expect(getMe()).rejects.toMatchObject({
      status: 502,
      message: "Request failed with 502",
    });
  });

  it("handles 204 responses without a body", async () => {
    setToken("t");
    fetchMock.mockResolvedValue(respond(204));

    await expect(removeMember(DOC_ID, "u2")).resolves.toBeUndefined();
    expect(fetchMock).toHaveBeenCalledWith(
      `/api/docs/${DOC_ID}/members/u2`,
      expect.objectContaining({ method: "DELETE" }),
    );
  });
});
