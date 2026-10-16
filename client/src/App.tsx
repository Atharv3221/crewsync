import { useCallback, useEffect, useState } from "react";
import { AuthPage } from "./components/AuthPage";
import { DocPage } from "./components/DocPage";
import { HomePage } from "./components/HomePage";
import { Notice } from "./components/Notice";
import {
  ApiError,
  createDoc,
  getMe,
  renameMe,
  type Session,
  type User,
} from "./lib/api";
import { clearToken, getToken, setToken } from "./lib/auth";
import {
  currentRoute,
  guardRoute,
  navigate,
  routeHash,
  type Route,
} from "./lib/routes";

type Account =
  | { readonly state: "anonymous" | "loading" | "failed" }
  | { readonly state: "ready"; readonly user: User };

const ANONYMOUS: Account = { state: "anonymous" };
const LOADING: Account = { state: "loading" };
const LOGIN: Route = { page: "login", next: null };

function initialAccount(): Account {
  return getToken() === null ? ANONYMOUS : LOADING;
}

/**
 * Hash router and session owner. Logged-out visitors are sent to the login page, which remembers
 * the doc they asked for; the user is loaded from the token on startup.
 */
export default function App() {
  const [route, setRoute] = useState(currentRoute);
  const [account, setAccount] = useState<Account>(initialAccount);
  const shown = guardRoute(route, account.state !== "anonymous");

  useEffect(() => {
    const onHashChange = () => {
      setRoute(currentRoute());
      if (getToken() === null) {
        setAccount(ANONYMOUS);
      }
    };
    window.addEventListener("hashchange", onHashChange);
    return () => window.removeEventListener("hashchange", onHashChange);
  }, []);

  useEffect(() => {
    if (routeHash(shown) !== window.location.hash) {
      history.replaceState(null, "", routeHash(shown));
    }
  }, [shown]);

  useEffect(() => {
    if (account.state === "loading") {
      getMe().then(
        (user) => setAccount({ state: "ready", user }),
        (error: unknown) => {
          const loggedOut = error instanceof ApiError && error.status === 401;
          setAccount(loggedOut ? ANONYMOUS : { state: "failed" });
        },
      );
    }
  }, [account.state]);

  const authenticated = useCallback((session: Session): void => {
    setToken(session.token);
    setAccount({ state: "ready", user: session.user });
  }, []);

  const logout = useCallback((): void => {
    clearToken();
    setAccount(ANONYMOUS);
    setRoute(LOGIN);
    navigate(LOGIN);
  }, []);

  const rename = useCallback(async (name: string): Promise<void> => {
    setAccount({ state: "ready", user: await renameMe(name) });
  }, []);

  const newDoc = useCallback(async (): Promise<void> => {
    try {
      navigate({ page: "doc", docId: (await createDoc()).id });
    } catch {
      // server unreachable or session expired: stay where we are
    }
  }, []);

  let page;
  if (shown.page === "login" || shown.page === "signup") {
    page = (
      <AuthPage
        key={shown.page}
        mode={shown.page}
        next={shown.next}
        onAuthenticated={authenticated}
      />
    );
  } else if (account.state === "ready") {
    const pageProps = {
      user: account.user,
      onRename: rename,
      onLogout: logout,
      onNewDoc: newDoc,
    };
    page =
      shown.page === "doc" ? (
        <DocPage key={shown.docId} docId={shown.docId} {...pageProps} />
      ) : (
        <HomePage {...pageProps} />
      );
  } else if (account.state === "failed") {
    page = (
      <Notice
        title="Can't reach the server"
        action="Retry"
        onAction={() => setAccount(LOADING)}
      />
    );
  } else {
    page = <Notice title="Loading…" />;
  }
  return page;
}
