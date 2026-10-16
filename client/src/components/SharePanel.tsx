import { useEffect, useState, type FormEvent } from "react";
import {
  ApiError,
  inviteMember,
  listMembers,
  removeMember,
  type DocRole,
  type Member,
} from "../lib/api";
import "./SharePanel.css";

interface SharePanelProps {
  readonly docId: string;
  readonly role: DocRole;
  readonly currentUserId: string;
}

const COPIED_MS = 1_500;

const ROLE_LABEL: Record<DocRole, string> = {
  OWNER: "Owner",
  EDITOR: "Editor",
};

/** Copies with the Clipboard API; returns false where it isn't available (plain http). */
async function copyText(text: string): Promise<boolean> {
  let copied = false;
  if (window.isSecureContext && navigator.clipboard !== undefined) {
    try {
      await navigator.clipboard.writeText(text);
      copied = true;
    } catch {
      copied = false;
    }
  }
  return copied;
}

function messageOf(error: unknown): string {
  return error instanceof ApiError ? error.message : "Can't reach the server";
}

/** Link sharing and the member list. Owners can also invite by email and remove editors. */
export function SharePanel({ docId, role, currentUserId }: SharePanelProps) {
  const [members, setMembers] = useState<readonly Member[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);
  const isOwner = role === "OWNER";

  useEffect(() => {
    let active = true;
    listMembers(docId).then(
      (loaded) => active && setMembers(loaded),
      (error: unknown) => active && setLoadError(messageOf(error)),
    );
    return () => {
      active = false;
    };
  }, [docId]);

  const copyLink = async (): Promise<void> => {
    const url = location.href;
    if (await copyText(url)) {
      setCopied(true);
      setTimeout(() => setCopied(false), COPIED_MS);
    } else {
      window.prompt("Copy this link:", url);
    }
  };

  const remove = async (userId: string): Promise<void> => {
    try {
      await removeMember(docId, userId);
      setMembers(
        (current) => current?.filter((m) => m.userId !== userId) ?? null,
      );
    } catch (error) {
      setLoadError(messageOf(error));
    }
  };

  return (
    <div className="popover share-panel" role="dialog" aria-label="Share">
      <div className="share-link">
        <p className="share-hint">Only members can open this link.</p>
        <button
          type="button"
          className="button"
          onClick={() => void copyLink()}
        >
          {copied ? "Copied!" : "Copy link"}
        </button>
      </div>

      {isOwner && (
        <InviteForm
          docId={docId}
          onInvited={(member) =>
            setMembers((current) => [...(current ?? []), member])
          }
        />
      )}

      <h2 className="share-heading">People with access</h2>
      {loadError !== null && <p className="field-error">{loadError}</p>}
      {members === null && loadError === null && (
        <p className="share-hint">Loading…</p>
      )}
      <ul className="member-list">
        {members?.map((member) => (
          <li key={member.userId} className="member">
            <div className="member-who">
              <span className="member-name">
                {member.name}
                {member.userId === currentUserId && " (you)"}
              </span>
              <span className="member-email">{member.email}</span>
            </div>
            <span className="member-role">{ROLE_LABEL[member.role]}</span>
            {isOwner && member.role === "EDITOR" && (
              <button
                type="button"
                className="member-remove"
                aria-label={`Remove ${member.name}`}
                onClick={() => void remove(member.userId)}
              >
                Remove
              </button>
            )}
          </li>
        ))}
      </ul>
    </div>
  );
}

interface InviteFormProps {
  readonly docId: string;
  readonly onInvited: (member: Member) => void;
}

function InviteForm({ docId, onInvited }: InviteFormProps) {
  const [email, setEmail] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const submit = async (event: FormEvent): Promise<void> => {
    event.preventDefault();
    if (email.trim() === "") {
      setError("Enter an email address");
      return;
    }
    setSubmitting(true);
    try {
      onInvited(await inviteMember(docId, email));
      setEmail("");
      setError(null);
    } catch (caught) {
      setError(messageOf(caught));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form
      className="invite-form"
      noValidate
      onSubmit={(event) => void submit(event)}
    >
      <label className="field-label" htmlFor="invite-email">
        Invite by email
      </label>
      <div className="invite-row">
        <input
          id="invite-email"
          className="text-input"
          type="email"
          autoComplete="off"
          placeholder="name@example.com"
          value={email}
          aria-invalid={error !== null}
          aria-describedby={error === null ? undefined : "invite-error"}
          onChange={(event) => {
            setEmail(event.target.value);
            setError(null);
          }}
        />
        <button
          type="submit"
          className="button button-primary"
          disabled={submitting}
        >
          Invite
        </button>
      </div>
      {error !== null && (
        <p id="invite-error" className="field-error">
          {error}
        </p>
      )}
    </form>
  );
}
