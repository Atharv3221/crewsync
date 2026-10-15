import { diffText, type CharId, type Op, type Rga } from "../crdt";

export class TextareaBinding {
    private readonly doc: Rga;
    private textarea: HTMLTextAreaElement | null = null;
    private composing = false;
    private held: Op[] = [];

    /**
     * Creates an instance of TextareaBinding.
     * @param doc - The underlying RGA document structure.
     */
    constructor(doc: Rga) {
        this.doc = doc;
    }

    attach(textarea: HTMLTextAreaElement | null): void {
        this.textarea = textarea;
        if (textarea !== null) {
            textarea.value = this.doc.text();
        }
    }

    /**
     * Computes differences between the current textarea state and the document,
     * turning them into local CRDT operations.
     * @returns An array of generated local operations.
     */
    readLocalEdit(): Op[] {
        const ops: Op[] = [];
        const ta = this.textarea;
        if (ta !== null) {
            const change = diffText(this.doc.text(), ta.value, ta.selectionEnd);
            for (let i = 0; i < change.deleteCount; i++) {
                ops.push(this.doc.localDelete(change.pos));
            }
            for (let i = 0; i < change.insertText.length; i++) {
                ops.push(this.doc.localInsert(change.pos + i, change.insertText[i]));
            }
        }
        return ops;
    }

    /**
     * Applies remote operations immediately, or holds/buffers them if an IME composition session is active.
     * @param ops - The list of remote operations to apply.
     */
    applyRemote(ops: readonly Op[]): void {
        if (this.composing) {
            this.held.push(...ops);
        } else {
            this.applyNow(ops);
        }
    }

    /** Marks the start of an IME text composition session (e.g., for CJK or accented input). */
    startComposition(): void {
        this.composing = true;
    }

    /**
     * Ends the IME composition session, captures any final local edits,
     * and flushes any remote operations that were held during composition.
     * @returns Local operations produced during the composition session.
     */
    endComposition(): Op[] {
        this.composing = false;
        const local = this.readLocalEdit();
        const held = this.held;
        this.held = [];
        this.applyNow(held);
        return local;
    }

    /**
     * Finds the user's current cursor position as a character identifier, suitable for presence indicators.
     * @returns The character ID at the cursor anchor, or null if unattached.
     */
    cursor(): CharId | null {
        return this.textarea === null ? null : this.anchorAt(this.textarea.selectionStart);
    }

    /**
     * Applies a list of operations directly to the document and updates the DOM textarea value,
     * carefully restoring the user's focus and selection range if focused.
     * @param ops - The operations to apply.
     */
    private applyNow(ops: readonly Op[]): void {
        const ta = this.textarea;
        const focused = ta !== null && document.activeElement === ta;
        const start = ta === null ? null : this.anchorAt(ta.selectionStart);
        const end = ta === null ? null : this.anchorAt(ta.selectionEnd);

        for (const op of ops) {
            this.doc.apply(op);
        }

        if (ta !== null) {
            const text = this.doc.text();
            if (ta.value !== text) {
                ta.value = text;
                if (focused) {
                    ta.setSelectionRange(this.doc.posOf(start), this.doc.posOf(end));
                }
            }
        }
    }

    /**
     * Maps a string character offset index to its corresponding CRDT `CharId`.
     * @param pos - The text position index.
     * @returns The corresponding character ID, or null.
     */
    private anchorAt(pos: number): CharId | null {
        return this.doc.idAt(Math.min(pos, this.doc.text().length));
    }

    /** True while an IME composition session is active. */
    get isComposing(): boolean {
        return this.composing;
    }
}
