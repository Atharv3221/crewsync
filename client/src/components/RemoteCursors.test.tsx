// @vitest-environment jsdom
import { cleanup, render } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import type { RemoteCursor } from "../collab";
import { RemoteCursors } from "./RemoteCursors";

function caret(site: string, name: string, pos: number): RemoteCursor {
  return { site, userId: site, name, color: "#123456", pos };
}

describe("RemoteCursors", () => {
  afterEach(cleanup);

  it("puts a named marker at each caret position inside the text", () => {
    const { container, getAllByTestId } = render(
      <RemoteCursors
        text={"ab\ncd"}
        cursors={[caret("s1", "Asha", 0), caret("s2", "Ravi", 3)]}
      />,
    );

    const overlay = container.firstElementChild as HTMLElement;
    expect(overlay.getAttribute("aria-hidden")).toBe("true");
    expect(overlay.textContent).toBe("Ashaab\nRavicd");
    const carets = getAllByTestId("remote-caret");
    expect(carets.map((node) => node.textContent)).toEqual(["Asha", "Ravi"]);
    expect(carets[0].style.getPropertyValue("--caret")).toBe("#123456");
  });

  it("clamps a caret past the end of the text and keeps a trailing newline", () => {
    const { container } = render(
      <RemoteCursors text={"ab\n"} cursors={[caret("s1", "Asha", 99)]} />,
    );

    expect(container.firstElementChild?.textContent).toBe("ab\nAsha");
  });
});
