import "./Notice.css";

interface NoticeProps {
  readonly title: string;
  readonly detail?: string;
  readonly action?: string;
  readonly onAction?: () => void;
}

/** A full-page message shown instead of the editor: loading, no access, or an error. */
export function Notice({ title, detail, action, onAction }: NoticeProps) {
  return (
    <main className="notice" role="status">
      <h1 className="notice-title">{title}</h1>
      {detail !== undefined && <p className="notice-detail">{detail}</p>}
      {action !== undefined && (
        <button type="button" className="notice-action" onClick={onAction}>
          {action}
        </button>
      )}
    </main>
  );
}
