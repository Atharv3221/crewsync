const TOKEN_KEY = "crewsync.token";

/** The stored session token, or null when logged out or storage is blocked. */
export function getToken(): string | null {
  let token: string | null;
  try {
    token = localStorage.getItem(TOKEN_KEY);
  } catch {
    token = null;
  }
  return token;
}

export function setToken(token: string): void {
  try {
    localStorage.setItem(TOKEN_KEY, token);
  } catch {
    // storage blocked: the session lasts until the page is reloaded
  }
}

export function clearToken(): void {
  try {
    localStorage.removeItem(TOKEN_KEY);
  } catch {
    // storage blocked: nothing was stored
  }
}
