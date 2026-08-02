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
    expect(screen.getByRole("button", { name: "닫기" })).toHaveFocus();
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

  it("명시적 닫기 버튼을 제공하고 클릭하면 onClose를 호출한다", () => {
    const onClose = vi.fn();
    render(<Dialog open onClose={onClose} titleId="t" title="제목">내용</Dialog>);

    fireEvent.click(screen.getByRole("button", { name: "닫기" }));
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

  // FE-099: useFocusTrap의 이펙트 의존성에 onClose가 있으면, 호출부가 인라인 함수를
  // 넘길 때 리렌더마다 cleanup→재설정이 돌아 포커스가 트리거로 되돌아가고 배경 inert가
  // 잠시 풀린다. 열린 상태에서 입력 중 포커스가 튀는 원인이다.
  it("열린 채로 부모가 리렌더돼도 포커스와 배경 격리가 유지된다", () => {
    function Harness({ tick }: { tick: number }) {
      // 매 렌더마다 새 함수 인스턴스를 넘긴다 — 실제 호출부(MobileSecondaryMenu)와 같은 형태.
      return (
        <>
          <button>배경 버튼</button>
          <Dialog open onClose={() => {}} titleId="t" title="제목">
            <input aria-label="첫 입력" defaultValue={String(tick)} />
            <input aria-label="둘째 입력" />
          </Dialog>
        </>
      );
    }

    const { rerender } = render(<Harness tick={0} />);
    // 트랩이 다시 설정되면 focusables[0](첫 입력)으로 포커스를 옮긴다. 두 번째 요소에
    // 포커스를 두어야 그 이동을 감지할 수 있다 — 첫 요소에 두면 우연히 같은 결과가 나온다.
    const second = screen.getByLabelText("둘째 입력");
    second.focus();
    expect(second).toHaveFocus();

    rerender(<Harness tick={1} />);

    expect(second).toHaveFocus();
    expect(screen.getByText("배경 버튼")).toHaveAttribute("inert");
    expect(document.body).toHaveStyle({ overflow: "hidden" });
  });
});
