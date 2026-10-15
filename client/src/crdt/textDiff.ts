/**
 * One contiguous edit: at `pos`, remove `deleteCount` chars, then insert `insertText`.
 */
export interface TextChange {
  readonly pos: number;
  readonly deleteCount: number;
  readonly insertText: string;
}

/**
 * Finds the single edit that turns `oldText` into `newText`.
 * `caret` is the cursor position in `newText` after the edit; it resolves
 * ambiguity when the same character repeats (e.g. "aa" -> "aaa").
 */
export function diffText(
  oldText: string,
  newText: string,
  caret: number,
): TextChange {
  const safeCaret = Math.min(Math.max(caret, 0), newText.length);

  const maxSuffix = Math.min(oldText.length, newText.length - safeCaret);
  let suffix = 0;
  while (
    suffix < maxSuffix &&
    oldText[oldText.length - 1 - suffix] ===
      newText[newText.length - 1 - suffix]
  ) {
    suffix++;
  }

  const maxPrefix = Math.min(oldText.length, newText.length) - suffix;
  let prefix = 0;
  while (prefix < maxPrefix && oldText[prefix] === newText[prefix]) {
    prefix++;
  }

  return {
    pos: prefix,
    deleteCount: oldText.length - prefix - suffix,
    insertText: newText.slice(prefix, newText.length - suffix),
  };
}
