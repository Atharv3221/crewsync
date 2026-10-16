import { clearToken, getToken } from "./auth";
import { apiUrl } from "./endpoints";
import { currentRoute, loginRoute, navigate } from "./routes";

export interface User {
  readonly id: string;
  readonly name: string;
  readonly email: string;
}

export type DocRole = "OWNER" | "EDITOR";

/** A document as seen by the current user, with their role in it. */
export interface Doc {
  readonly id: string;
  readonly title: string;
  /** Goes up by one on every rename; the higher version is the newer title. */
  readonly titleVersion: number;
  readonly role: DocRole;
  readonly createdAt: string;
}

export interface Member {
  readonly userId: string;
  readonly name: string;
  readonly email: string;
  readonly role: DocRole;
}

export interface Session {
  readonly token: string;
  readonly user: User;
}

/** A non-2xx response. `message` is the server's own message when it sent one. */
export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

function headersFor(
  token: string | null,
  hasBody: boolean,
): Record<string, string> {
  const headers: Record<string, string> = {};
  if (token !== null) {
    headers.Authorization = `Bearer ${token}`;
  }
  if (hasBody) {
    headers["Content-Type"] = "application/json";
  }
  return headers;
}

async function errorFor(response: Response): Promise<ApiError> {
  let message = `Request failed with ${response.status}`;
  try {
    const body: unknown = await response.json();
    if (
      typeof body === "object" &&
      body !== null &&
      "message" in body &&
      typeof body.message === "string"
    ) {
      message = body.message;
    }
  } catch {
    // not JSON: keep the generic message
  }
  return new ApiError(response.status, message);
}

/** The session is gone or was never valid: drop it and ask the user to log in again. */
function handleUnauthorized(): void {
  clearToken();
  navigate(loginRoute(currentRoute()));
}

async function request<T>(
  method: string,
  path: string,
  body?: unknown,
): Promise<T> {
  const token = getToken();
  const response = await fetch(apiUrl(path), {
    method,
    headers: headersFor(token, body !== undefined),
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (response.status === 401 && token !== null) {
    handleUnauthorized();
  }
  if (!response.ok) {
    throw await errorFor(response);
  }
  return (response.status === 204 ? undefined : await response.json()) as T;
}

const docPath = (id: string): string => `/api/docs/${encodeURIComponent(id)}`;

export function signup(
  name: string,
  email: string,
  password: string,
): Promise<Session> {
  return request("POST", "/api/auth/signup", { name, email, password });
}

export function login(email: string, password: string): Promise<Session> {
  return request("POST", "/api/auth/login", { email, password });
}

export function getMe(): Promise<User> {
  return request("GET", "/api/me");
}

export function renameMe(name: string): Promise<User> {
  return request("PATCH", "/api/me", { name });
}

/** My docs, newest first. */
export function listMyDocs(): Promise<Doc[]> {
  return request("GET", "/api/me/docs");
}

export function createDoc(title?: string): Promise<Doc> {
  return request("POST", "/api/docs", { title });
}

export function getDoc(id: string): Promise<Doc> {
  return request("GET", docPath(id));
}

/** Renames a doc. Any member may rename; everyone in the doc sees the new title live. */
export function renameDoc(id: string, title: string): Promise<Doc> {
  return request("PATCH", docPath(id), { title });
}

export function listMembers(docId: string): Promise<Member[]> {
  return request("GET", `${docPath(docId)}/members`);
}

/** Invites a registered user as an editor. Owner only. */
export function inviteMember(docId: string, email: string): Promise<Member> {
  return request("POST", `${docPath(docId)}/members`, { email });
}

export function removeMember(docId: string, userId: string): Promise<void> {
  return request(
    "DELETE",
    `${docPath(docId)}/members/${encodeURIComponent(userId)}`,
  );
}
