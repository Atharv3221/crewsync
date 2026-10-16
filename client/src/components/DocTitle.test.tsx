// @vitest-environment jsdom
import { act, cleanup, fireEvent, render } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { DocTitle } from "./DocTitle";

function setup(onRename = vi.fn(() => Promise.resolve())) {
  const view = render(<DocTitle title="Plans" editable onRename={onRename} />);
  const input = view.getByLabelText("Document title") as HTMLInputElement;
  return { ...view, input, onRename };
}

describe("DocTitle", () => {
  afterEach(cleanup);

  it("saves the trimmed title on Enter", () => {
    const { input, onRename } = setup();

    fireEvent.focus(input);
    fireEvent.change(input, { target: { value: "  Launch plan " } });
    fireEvent.keyDown(input, { key: "Enter" });
    fireEvent.blur(input);

    expect(onRename).toHaveBeenCalledExactlyOnceWith("Launch plan");
  });

  it("does not save an unchanged or blank title", () => {
    const { input, onRename } = setup();

    fireEvent.focus(input);
    fireEvent.blur(input);
    fireEvent.focus(input);
    fireEvent.change(input, { target: { value: "   " } });
    fireEvent.blur(input);

    expect(onRename).not.toHaveBeenCalled();
    expect(input.value).toBe("Plans");
  });

  it("cancels on Escape", () => {
    const { input, onRename } = setup();

    fireEvent.focus(input);
    fireEvent.change(input, { target: { value: "Oops" } });
    fireEvent.keyDown(input, { key: "Escape" });
    fireEvent.blur(input);

    expect(onRename).not.toHaveBeenCalled();
    expect(input.value).toBe("Plans");
  });

  it("keeps following renames while focused, and an untouched blur saves nothing", () => {
    const { input, rerender, onRename } = setup();

    fireEvent.focus(input);
    rerender(<DocTitle title="Their title" editable onRename={onRename} />);
    expect(input.value).toBe("Their title");
    fireEvent.blur(input);

    expect(onRename).not.toHaveBeenCalled();
  });

  it("follows a title changed by someone else", () => {
    const { input, rerender, onRename } = setup();

    rerender(<DocTitle title="Their title" editable onRename={onRename} />);

    expect(input.value).toBe("Their title");
  });

  it("flags a failed save", async () => {
    const { input } = setup(vi.fn(() => Promise.reject(new Error("403"))));

    fireEvent.focus(input);
    fireEvent.change(input, { target: { value: "New" } });
    await act(async () => {
      fireEvent.blur(input);
    });

    expect(input.className).toContain("doc-title-error");
    expect(input.title).toBe("Couldn't rename the doc");
  });

  it("is read-only for anyone but the owner", () => {
    const onRename = vi.fn(() => Promise.resolve());
    const { queryByLabelText, getByText } = render(
      <DocTitle title="Plans" editable={false} onRename={onRename} />,
    );

    expect(queryByLabelText("Document title")).toBeNull();
    expect(getByText("Plans").title).toBe(
      "Plans · only the owner can rename this doc",
    );
  });
});
