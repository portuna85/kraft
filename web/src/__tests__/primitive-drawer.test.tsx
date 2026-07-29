import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { Drawer } from "@/ui/primitives/drawer";

afterEach(cleanup);

describe("Drawer 프리미티브", () => {
  it("open=false면 아무것도 렌더링하지 않는다", () => {
    const { container } = render(
      <Drawer open={false} onClose={() => {}} side="right" titleId="t" title="필터">
        내용
      </Drawer>
    );
    expect(container).toBeEmptyDOMElement();
  });

  it("open=true면 role=dialog로 렌더링한다", () => {
    render(
      <Drawer open onClose={() => {}} side="bottom" titleId="drawer-title" title="필터">
        내용
      </Drawer>
    );
    const drawer = screen.getByRole("dialog");
    expect(drawer).toHaveAttribute("aria-labelledby", "drawer-title");
    expect(drawer).toHaveAttribute("tabindex", "-1");
    expect(screen.getByRole("button", { name: "닫기" })).toHaveFocus();
  });

  it("Escape 키를 누르면 onClose를 호출한다", () => {
    const onClose = vi.fn();
    render(
      <Drawer open onClose={onClose} side="left" titleId="t" title="필터">
        <button>버튼</button>
      </Drawer>
    );
    fireEvent.keyDown(document, { key: "Escape" });
    expect(onClose).toHaveBeenCalledOnce();
  });
});
