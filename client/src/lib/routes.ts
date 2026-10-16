/**
 * Hash routes: `#/login`, `#/signup`, `#/` (my docs) and `#<docId>` (editor).
 * The auth pages carry `?next=<docId>` so a login returns to the doc that was asked for.
 */
export type Route =
  | { readonly page: "login" | "signup"; readonly next: string | null }
  | { readonly page: "home" }
  | { readonly page: "doc"; readonly docId: string };

const HOME: Route = { page: "home" };

function parseAuthRoute(page: "login" | "signup", query: string): Route {
  const next = new URLSearchParams(query).get("next");
  return { page, next: next === null || next === "" ? null : next };
}

export function parseRoute(hash: string): Route {
  const raw = hash.startsWith("#") ? hash.slice(1) : hash;
  const [path, query = ""] = raw.split("?", 2);
  let route: Route = HOME;
  if (path === "/login" || path === "/signup") {
    route = parseAuthRoute(path === "/login" ? "login" : "signup", query);
  } else if (path !== "" && !path.startsWith("/")) {
    route = { page: "doc", docId: decodeURIComponent(path) };
  }
  return route;
}

export function routeHash(route: Route): string {
  let hash = "#/";
  if (route.page === "doc") {
    hash = `#${encodeURIComponent(route.docId)}`;
  } else if (route.page === "login" || route.page === "signup") {
    const next =
      route.next === null ? "" : `?next=${encodeURIComponent(route.next)}`;
    hash = `#/${route.page}${next}`;
  }
  return hash;
}

/** The login page that returns to `from` afterwards when it is a doc. */
export function loginRoute(from: Route): Route {
  let next: string | null = null;
  if (from.page === "doc") {
    next = from.docId;
  } else if (from.page === "login" || from.page === "signup") {
    next = from.next;
  }
  return { page: "login", next };
}

/** Where to go once logged in: the remembered doc, or my docs. */
export function routeAfterLogin(route: Route): Route {
  const next =
    route.page === "login" || route.page === "signup" ? route.next : null;
  return next === null ? HOME : { page: "doc", docId: next };
}

/**
 * The route guard. Logged-out visitors only see the auth pages; logged-in users skip them.
 * Returns `route` itself when it may be shown.
 */
export function guardRoute(route: Route, loggedIn: boolean): Route {
  const onAuthPage = route.page === "login" || route.page === "signup";
  let allowed = route;
  if (!loggedIn && !onAuthPage) {
    allowed = loginRoute(route);
  } else if (loggedIn && onAuthPage) {
    allowed = routeAfterLogin(route);
  }
  return allowed;
}

export function currentRoute(): Route {
  return parseRoute(window.location.hash);
}

export function navigate(route: Route): void {
  window.location.hash = routeHash(route);
}
