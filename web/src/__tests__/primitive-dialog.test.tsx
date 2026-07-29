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
    expect(dialog).toHaveAttribute("tabindex", "-1");
    expect(dialog).toHaveFocus();
    expect(document.body).toHaveStyle({ overflow: "hidden" });
  });

  it("닫히면 body의 기존 스크롤 값을 복원한다", () => {
    document.body.style.overflow = "auto";
    const { rerender } = render(<Dialog open onClose={() => {}} titleId="t" title="제목">내용</Dialog>);
    rerender(<Dialog open={false} onClose={() => {}} titleId="t" title="제목">내용</Dialog>);
    expect(document.body).toHaveStyle({ overflow: "auto" });
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

  it("열린 동안 배경 콘텐츠를 접근성 트리와 포커스 순서에서 제외한다", () => {
    render(
      <>
        <button>배경 버튼</button>
        <Dialog open onClose={() => {}} titleId="t" title="제목">내용</Dialog>
      </>
    );
    const background = screen.getByText("배경 버튼");
    expect(background).toHaveAttribute("aria-hidden", "true");
    expect(background).toHaveAttribute("inert");
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
