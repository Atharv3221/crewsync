/**
 * Where the API lives. VITE_API_URL (e.g. https://crewsync-api.onrender.com) is set when the
 * client is deployed apart from the server; unset, everything goes to this page's own origin.
 */
const API_BASE = trimSlashes(import.meta.env.VITE_API_URL ?? "");

function trimSlashes(url: string): string {
  return url.trim().replace(/\/+$/, "");
}

/** Full URL for an API path such as `/api/me`. */
export function apiUrl(path: string, base: string = API_BASE): string {
  return base + path;
}

/**
 * Origin (plus any base path) for WebSocket URLs: the API base with http→ws and https→wss, or
 * this page's own host when no base is configured.
 */
export function socketBase(
  base: string = API_BASE,
  page: Pick<Location, "protocol" | "host"> = location,
): string {
  let result: string;
  if (base === "") {
    result = `${page.protocol === "https:" ? "wss:" : "ws:"}//${page.host}`;
  } else {
    const url = new URL(base);
    const protocol = url.protocol === "https:" ? "wss:" : "ws:";
    result = `${protocol}//${url.host}${trimSlashes(url.pathname)}`;
  }
  return result;
}
