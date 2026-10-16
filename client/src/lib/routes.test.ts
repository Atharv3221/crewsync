import { describe, expect, it } from "vitest";
import {
  guardRoute,
  loginRoute,
  parseRoute,
  routeAfterLogin,
  routeHash,
  type Route,
} from "./routes.ts";

const DOC_ID = "3f9a0c7b-e2d4-41a6-8b1c-0d2e3f4a5b6c";

describe("parseRoute", () => {
  it.each<[string, Route]>([
    ["", { page: "home" }],
    ["#", { page: "home" }],
    ["#/", { page: "home" }],
    ["#/unknown", { page: "home" }],
    ["#/login", { page: "login", next: null }],
    [`#/login?next=${DOC_ID}`, { page: "login", next: DOC_ID }],
    ["#/signup", { page: "signup", next: null }],
    [`#${DOC_ID}`, { page: "doc", docId: DOC_ID }],
  ])("reads %j", (hash, route) => {
    expect(parseRoute(hash)).toEqual(route);
  });

  it.each<Route>([
    { page: "home" },
    { page: "login", next: null },
    { page: "signup", next: DOC_ID },
    { page: "doc", docId: DOC_ID },
  ])("round-trips %j through routeHash", (route) => {
    expect(parseRoute(routeHash(route))).toEqual(route);
  });
});

describe("guardRoute", () => {
  it("sends a logged-out visitor of a doc to login, remembering the doc", () => {
    const guarded = guardRoute({ page: "doc", docId: DOC_ID }, false);

    expect(guarded).toEqual({ page: "login", next: DOC_ID });
    expect(routeHash(guarded)).toBe(`#/login?next=${DOC_ID}`);
  });

  it("sends a logged-out visitor of the home page to login", () => {
    expect(guardRoute({ page: "home" }, false)).toEqual({
      page: "login",
      next: null,
    });
  });

  it("lets logged-out visitors stay on the auth pages", () => {
    const signup: Route = { page: "signup", next: DOC_ID };
    expect(guardRoute(signup, false)).toBe(signup);
  });

  it("returns to the remembered doc once logged in", () => {
    const login = guardRoute({ page: "doc", docId: DOC_ID }, false);

    expect(guardRoute(login, true)).toEqual({ page: "doc", docId: DOC_ID });
  });

  it("goes to my docs after logging in without a remembered doc", () => {
    expect(guardRoute({ page: "login", next: null }, true)).toEqual({
      page: "home",
    });
  });

  it("lets logged-in users through", () => {
    const doc: Route = { page: "doc", docId: DOC_ID };
    expect(guardRoute(doc, true)).toBe(doc);
  });
});

describe("loginRoute and routeAfterLogin", () => {
  it("keeps the remembered doc when switching between login and signup", () => {
    expect(loginRoute({ page: "signup", next: DOC_ID })).toEqual({
      page: "login",
      next: DOC_ID,
    });
  });

  it("ignores pages that aren't auth pages", () => {
    expect(routeAfterLogin({ page: "home" })).toEqual({ page: "home" });
  });
});
