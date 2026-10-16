import { useState, type FormEvent } from "react";
import { ApiError, login, signup, type Session } from "../lib/api";
import { routeHash } from "../lib/routes";
import "./AuthPage.css";

type Mode = "login" | "signup";
type Field = "name" | "email" | "password";
type FieldErrors = Partial<Record<Field, string>>;

interface AuthPageProps {
  readonly mode: Mode;
  readonly next: string | null;
  readonly onAuthenticated: (session: Session) => void;
}

const MIN_PASSWORD_LENGTH = 8;
const EMAIL_FORMAT = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;

const COPY: Record<
  Mode,
  { title: string; submit: string; switchPrompt: string; switchLink: string }
> = {
  login: {
    title: "Log in to Crewrite",
    submit: "Log in",
    switchPrompt: "New here?",
    switchLink: "Create an account",
  },
  signup: {
    title: "Create your account",
    submit: "Sign up",
    switchPrompt: "Already have an account?",
    switchLink: "Log in",
  },
};

function validate(
  mode: Mode,
  name: string,
  email: string,
  password: string,
): FieldErrors {
  const errors: FieldErrors = {};
  if (mode === "signup" && name.trim() === "") {
    errors.name = "Enter your name";
  }
  if (!EMAIL_FORMAT.test(email.trim())) {
    errors.email = "Enter a valid email address";
  }
  if (mode === "signup" && password.length < MIN_PASSWORD_LENGTH) {
    errors.password = `Use at least ${MIN_PASSWORD_LENGTH} characters`;
  } else if (password === "") {
    errors.password = "Enter your password";
  }
  return errors;
}

function messageOf(error: unknown): string {
  return error instanceof ApiError
    ? error.message
    : "Can't reach the server. Try again.";
}

/** Login and signup in one form; the toggle link switches between them. */
export function AuthPage({ mode, next, onAuthenticated }: AuthPageProps) {
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [errors, setErrors] = useState<FieldErrors>({});
  const [serverError, setServerError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const copy = COPY[mode];
  const otherMode: Mode = mode === "login" ? "signup" : "login";

  const submit = async (event: FormEvent): Promise<void> => {
    event.preventDefault();
    const found = validate(mode, name, email, password);
    setErrors(found);
    setServerError(null);
    if (Object.keys(found).length > 0) {
      return;
    }
    setSubmitting(true);
    try {
      const session =
        mode === "login"
          ? await login(email, password)
          : await signup(name, email, password);
      onAuthenticated(session);
    } catch (error) {
      setServerError(messageOf(error));
      setSubmitting(false);
    }
  };

  const fieldProps = (field: Field) => ({
    id: `auth-${field}`,
    className: "text-input",
    "aria-invalid": errors[field] !== undefined,
    "aria-describedby":
      errors[field] === undefined ? undefined : `auth-${field}-error`,
  });

  const fieldError = (field: Field) =>
    errors[field] !== undefined && (
      <p id={`auth-${field}-error`} className="field-error">
        {errors[field]}
      </p>
    );

  return (
    <main className="auth">
      <form
        className="auth-card"
        noValidate
        onSubmit={(event) => void submit(event)}
      >
        <span className="logo">Crewrite</span>
        <h1 className="auth-title">{copy.title}</h1>

        {mode === "signup" && (
          <div className="auth-field">
            <label className="field-label" htmlFor="auth-name">
              Name
            </label>
            <input
              {...fieldProps("name")}
              autoComplete="name"
              maxLength={100}
              value={name}
              onChange={(event) => setName(event.target.value)}
            />
            {fieldError("name")}
          </div>
        )}

        <div className="auth-field">
          <label className="field-label" htmlFor="auth-email">
            Email
          </label>
          <input
            {...fieldProps("email")}
            type="email"
            autoComplete="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
          />
          {fieldError("email")}
        </div>

        <div className="auth-field">
          <label className="field-label" htmlFor="auth-password">
            Password
          </label>
          <input
            {...fieldProps("password")}
            type="password"
            autoComplete={
              mode === "login" ? "current-password" : "new-password"
            }
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
          {fieldError("password")}
        </div>

        {serverError !== null && (
          <p className="auth-error" role="alert">
            {serverError}
          </p>
        )}

        <button
          type="submit"
          className="button button-primary auth-submit"
          disabled={submitting}
        >
          {submitting ? "Please wait…" : copy.submit}
        </button>

        <p className="auth-switch">
          {copy.switchPrompt}{" "}
          <a href={routeHash({ page: otherMode, next })}>{copy.switchLink}</a>
        </p>
      </form>
    </main>
  );
}
