import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { ConfirmDialog } from "@/ui/primitives/confirm-dialog";

afterEach(cleanup);

const BASE = {
  title: "삭제할까요?",
  confirmLabel: "삭제",
  onConfirm: () => {},
  onCancel: () => {},
};

// FE-003: window.confirm(3곳)·5초 유예 undo(1곳)·확인 없음(2곳)이 섞여 있던 것을
// 이 다이얼로그 하나로 통일했다.
describe("ConfirmDialog 프리미티브", () => {
  it("open=false면 아무것도 렌더링하지 않는다", () => {
    const { container } = render(<ConfirmDialog {...BASE} open={false} />);
    expect(container).toBeEmptyDOMElement();
  });

  it("제목과 설명을 보여주고 확인·취소를 제공한다", () => {
    render(<ConfirmDialog {...BASE} open description="되돌릴 수 없습니다." />);

    const dialog = screen.getByRole("dialog");
    expect(dialog).toHaveTextContent("삭제할까요?");
    expect(dialog).toHaveTextContent("되돌릴 수 없습니다.");
    expect(screen.getByRole("button", { name: "삭제" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "취소" })).toBeInTheDocument();
  });

  it("확인·취소가 각각의 콜백을 부른다", () => {
    const onConfirm = vi.fn();
    const onCancel = vi.fn();
    render(<ConfirmDialog {...BASE} open onConfirm={onConfirm} onCancel={onCancel} />);

    fireEvent.click(screen.getByRole("button", { name: "삭제" }));
    expect(onConfirm).toHaveBeenCalledOnce();

    fireEvent.click(screen.getByRole("button", { name: "취소" }));
    expect(onCancel).toHaveBeenCalledOnce();
  });

  it("진행 중에는 양쪽 버튼을 잠가 중복 실행을 막는다", () => {
    render(<ConfirmDialog {...BASE} open pending />);

    expect(screen.getByRole("button", { name: "처리 중…" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "취소" })).toBeDisabled();
  });

  // 요청이 나간 뒤 닫히면 결과를 보여줄 자리가 없다.
  it("진행 중에는 Escape로 닫히지 않는다", () => {
    const onCancel = vi.fn();
    render(<ConfirmDialog {...BASE} open pending onCancel={onCancel} />);

    fireEvent.keyDown(document, { key: "Escape" });

    expect(onCancel).not.toHaveBeenCalled();
  });

  it("진행 중이 아니면 Escape로 취소된다", () => {
    const onCancel = vi.fn();
    render(<ConfirmDialog {...BASE} open onCancel={onCancel} />);

    fireEvent.keyDown(document, { key: "Escape" });

    expect(onCancel).toHaveBeenCalled();
  });

  // L-9: initialFocusRef가 없으면 트랩이 패널의 첫 포커스 가능 요소(Dialog 헤더의
  // 닫기 버튼)로 가버려, 파괴적 확인의 기본 포커스가 "취소"가 아니게 된다.
  it("열리면 취소 버튼으로 초기 포커스가 간다(닫기 버튼이 아니라)", () => {
    render(<ConfirmDialog {...BASE} open />);

    expect(screen.getByRole("button", { name: "취소" })).toHaveFocus();
  });

  it("실패 사유를 다이얼로그 안에서 알린다", () => {
    render(<ConfirmDialog {...BASE} open errorMessage="삭제하지 못했습니다." />);

    expect(screen.getByRole("alert")).toHaveTextContent("삭제하지 못했습니다.");
    // 실패해도 닫지 않아 다시 시도할 수 있어야 한다.
    expect(screen.getByRole("button", { name: "삭제" })).toBeEnabled();
  });
});
