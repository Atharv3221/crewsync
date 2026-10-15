import { describe, expect, it } from "vitest";
import { diffText, type TextChange } from "./index";

/** Applies a change to text: the inverse of diffText. */
function applyChange(text: string, change: TextChange): string {
  return (
    text.slice(0, change.pos) +
    change.insertText +
    text.slice(change.pos + change.deleteCount)
  );
}

describe("diffText", () => {
  it.each([
    {
      name: "typing in the middle",
      before: "cat",
      after: "chat",
      caret: 2,
      pos: 1,
      deleteCount: 0,
      insertText: "h",
    },
    {
      name: "typing at the start",
      before: "cat",
      after: "xcat",
      caret: 1,
      pos: 0,
      deleteCount: 0,
      insertText: "x",
    },
    {
      name: "typing at the end",
      before: "cat",
      after: "cats",
      caret: 4,
      pos: 3,
      deleteCount: 0,
      insertText: "s",
    },
    {
      name: "backspace",
      before: "abc",
      after: "ac",
      caret: 1,
      pos: 1,
      deleteCount: 1,
      insertText: "",
    },
    {
      name: "select and replace",
      before: "abc",
      after: "aXYc",
      caret: 3,
      pos: 1,
      deleteCount: 1,
      insertText: "XY",
    },
    {
      name: "paste over everything",
      before: "hello",
      after: "world",
      caret: 5,
      pos: 0,
      deleteCount: 5,
      insertText: "world",
    },
    {
      name: "select all and delete",
      before: "hello",
      after: "",
      caret: 0,
      pos: 0,
      deleteCount: 5,
      insertText: "",
    },
    {
      name: "repeated chars, typed at 0",
      before: "aa",
      after: "aaa",
      caret: 1,
      pos: 0,
      deleteCount: 0,
      insertText: "a",
    },
    {
      name: "repeated chars, typed at 1",
      before: "aa",
      after: "aaa",
      caret: 2,
      pos: 1,
      deleteCount: 0,
      insertText: "a",
    },
    {
      name: "repeated chars, typed at 2",
      before: "aa",
      after: "aaa",
      caret: 3,
      pos: 2,
      deleteCount: 0,
      insertText: "a",
    },
    {
      name: "repeated chars, deleted first",
      before: "aaa",
      after: "aa",
      caret: 0,
      pos: 0,
      deleteCount: 1,
      insertText: "",
    },
    {
      name: "repeated chars, deleted middle",
      before: "aaa",
      after: "aa",
      caret: 1,
      pos: 1,
      deleteCount: 1,
      insertText: "",
    },
    {
      name: "emoji is two code units",
      before: "hi",
      after: "h😀i",
      caret: 3,
      pos: 1,
      deleteCount: 0,
      insertText: "😀",
    },
  ])("$name", ({ before, after, caret, pos, deleteCount, insertText }) => {
    const change = diffText(before, after, caret);
    expect(change).toEqual({ pos, deleteCount, insertText });
    expect(applyChange(before, change)).toBe(after);
  });

  it("reports no edit when the text is unchanged", () => {
    const change = diffText("same", "same", 2);
    expect(change.deleteCount).toBe(0);
    expect(change.insertText).toBe("");
  });

  it("survives an out-of-range caret and still reproduces the new text", () => {
    const change = diffText("cat", "chat", 99);
    expect(applyChange("cat", change)).toBe("chat");
  });

  it("always reproduces the new text for random edits (1000 cases)", () => {
    let state = 42;
    const randomInt = (n: number) => {
      state = (state * 1664525 + 1013904223) >>> 0;
      return Math.floor((state / 2 ** 32) * n);
    };
    const randomText = (length: number) =>
      Array.from({ length }, () => "ab"[randomInt(2)]).join(""); // only a/b → lots of ambiguity

    for (let i = 0; i < 1000; i++) {
      const before = randomText(randomInt(10));
      const start = randomInt(before.length + 1);
      const removed = randomInt(before.length - start + 1);
      const inserted = randomText(randomInt(4));
      const after =
        before.slice(0, start) + inserted + before.slice(start + removed);
      const caret = start + inserted.length;

      const change = diffText(before, after, caret);
      expect(applyChange(before, change), `case ${i}`).toBe(after);
    }
  });
});
