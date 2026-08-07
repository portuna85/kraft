import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";

import { ConfirmDialog, Dialog } from "./dialog";

describe("Dialog", () => {
  it("닫힌 상태에서는 DOM에 아무것도 남기지 않는다", () => {
    render(
      <Dialog open={false} onClose={vi.fn()} title="제목">
        내용
      </Dialog>,
    );
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("제목을 다이얼로그의 접근 이름으로 연결한다", () => {
    render(
      <Dialog open onClose={vi.fn()} title="번호 삭제">
        내용
      </Dialog>,
    );
    expect(screen.getByRole("dialog", { name: "번호 삭제" })).toBeInTheDocument();
  });

  it("열리면 포커스가 다이얼로그 안으로 들어간다", () => {
    render(
      <Dialog open onClose={vi.fn()} title="제목">
        <button type="button">확인</button>
      </Dialog>,
    );
    expect(screen.getByRole("dialog").contains(document.activeElement)).toBe(true);
  });

  it("Esc로 닫힌다", async () => {
    const onClose = vi.fn();
    render(
      <Dialog open onClose={onClose} title="제목">
        내용
      </Dialog>,
    );

    await userEvent.keyboard("{Escape}");
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("배경을 클릭하면 닫히지만 패널 내부 클릭은 닫지 않는다", async () => {
    const onClose = vi.fn();
    render(
      <Dialog open onClose={onClose} title="제목">
        <p>본문</p>
      </Dialog>,
    );

    await userEvent.click(screen.getByText("본문"));
    expect(onClose).not.toHaveBeenCalled();
  });

  it("닫을 때 포커스를 열기 전 요소로 되돌린다", async () => {
    function Harness() {
      const [open, setOpen] = useState(false);
      return (
        <>
          <button type="button" onClick={() => setOpen(true)}>
            열기
          </button>
          <Dialog open={open} onClose={() => setOpen(false)} title="제목">
            <button type="button" onClick={() => setOpen(false)}>
              확인
            </button>
          </Dialog>
        </>
      );
    }

    render(<Harness />);
    const opener = screen.getByRole("button", { name: "열기" });
    await userEvent.click(opener);
    await userEvent.click(screen.getByRole("button", { name: "확인" }));

    expect(document.activeElement).toBe(opener);
  });
});

describe("ConfirmDialog", () => {
  it("확인 버튼을 누르면 onConfirm이 호출된다", async () => {
    const onConfirm = vi.fn();
    render(
      <ConfirmDialog
        open
        onClose={vi.fn()}
        title="삭제할까요?"
        description="되돌릴 수 없습니다."
        confirmLabel="삭제"
        onConfirm={onConfirm}
      />,
    );

    await userEvent.click(screen.getByRole("button", { name: "삭제" }));
    expect(onConfirm).toHaveBeenCalledTimes(1);
  });

  it("처리 중에는 확인과 취소를 모두 잠근다", () => {
    render(
      <ConfirmDialog
        open
        onClose={vi.fn()}
        title="삭제할까요?"
        description="되돌릴 수 없습니다."
        confirmLabel="삭제"
        onConfirm={vi.fn()}
        pending
      />,
    );

    expect(screen.getByRole("button", { name: "취소" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "처리 중" })).toBeDisabled();
  });

  it("실패를 다이얼로그 안에서 보여준다", () => {
    render(
      <ConfirmDialog
        open
        onClose={vi.fn()}
        title="삭제할까요?"
        description="되돌릴 수 없습니다."
        confirmLabel="삭제"
        onConfirm={vi.fn()}
        errorMessage="삭제하지 못했습니다."
      />,
    );

    const dialog = screen.getByRole("dialog");
    expect(dialog).toHaveTextContent("삭제하지 못했습니다.");
  });
});
