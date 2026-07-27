import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { Dialog } from "@/ui/primitives/dialog";

afterEach(cleanup);

describe("Dialog 프리미티브", () => {
  it("open=false면 아무것도 렌더링하지 않는다", () => {
    const { container } = render(
      <Dialog open={false} onClose={() => {}} titleId="t" title="제목">
        내용
      </Dialog>
    );
    expect(container).toBeEmptyDOMElement();
  });

  it("open=true면 role=dialog와 aria-labelledby를 렌더링한다", () => {
    render(
      <Dialog open onClose={() => {}} titleId="dialog-title" title="확인">
        내용
      </Dialog>
    );
    const dialog = screen.getByRole("dialog");
    expect(dialog).toHaveAttribute("aria-labelledby", "dialog-title");
    expect(dialog).toHaveAttribute("aria-modal", "true");
  });

  it("Escape 키를 누르면 onClose를 호출한다", () => {
    const onClose = vi.fn();
    render(
      <Dialog open onClose={onClose} titleId="t" title="제목">
        <button>포커스 대상</button>
      </Dialog>
    );
    fireEvent.keyDown(document, { key: "Escape" });
    expect(onClose).toHaveBeenCalledOnce();
  });

  it("배경(backdrop) 클릭 시 onClose를 호출하지만 패널 내부 클릭은 호출하지 않는다", () => {
    const onClose = vi.fn();
    render(
      <Dialog open onClose={onClose} titleId="t" title="제목">
        <button>버튼</button>
      </Dialog>
    );
    screen.getByRole("button", { name: "버튼" }).click();
    expect(onClose).not.toHaveBeenCalled();
    screen.getByRole("dialog").parentElement?.click();
    expect(onClose).toHaveBeenCalledOnce();
  });
});
