import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";

import { Drawer } from "./drawer";

function ChangingOnCloseHarness({ onCloseWithId }: { onCloseWithId: (id: number) => void }) {
  const [id, setId] = useState(1);
  return (
    <>
      <button type="button" onClick={() => setId((current) => current + 1)}>
        id 변경
      </button>
      <Drawer open onClose={() => onCloseWithId(id)} title="제목">
        내용
      </Drawer>
    </>
  );
}

describe("Drawer", () => {
  it("닫힌 상태에서는 DOM에 아무것도 남기지 않는다", () => {
    render(
      <Drawer open={false} onClose={vi.fn()} title="제목">
        내용
      </Drawer>,
    );
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("제목을 드로어의 접근 이름으로 연결한다", () => {
    render(
      <Drawer open onClose={vi.fn()} title="필터">
        내용
      </Drawer>,
    );
    expect(screen.getByRole("dialog", { name: "필터" })).toBeInTheDocument();
  });

  it("열리면 포커스가 드로어 안으로 들어간다", () => {
    render(
      <Drawer open onClose={vi.fn()} title="제목">
        <button type="button">확인</button>
      </Drawer>,
    );
    expect(screen.getByRole("dialog").contains(document.activeElement)).toBe(true);
  });

  it("Esc로 닫힌다", async () => {
    const onClose = vi.fn();
    render(
      <Drawer open onClose={onClose} title="제목">
        내용
      </Drawer>,
    );

    await userEvent.keyboard("{Escape}");
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("가시적 닫기 버튼으로 닫을 수 있다", async () => {
    const onClose = vi.fn();
    render(
      <Drawer open onClose={onClose} title="제목">
        내용
      </Drawer>,
    );

    await userEvent.click(screen.getByRole("button", { name: "닫기" }));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("배경을 클릭하면 닫히지만 패널 내부 클릭은 닫지 않는다", async () => {
    const onClose = vi.fn();
    render(
      <Drawer open onClose={onClose} title="제목">
        <p>본문</p>
      </Drawer>,
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
          <Drawer open={open} onClose={() => setOpen(false)} title="제목">
            <button type="button" onClick={() => setOpen(false)}>
              확인
            </button>
          </Drawer>
        </>
      );
    }

    render(<Harness />);
    const opener = screen.getByRole("button", { name: "열기" });
    await userEvent.click(opener);
    await userEvent.click(screen.getByRole("button", { name: "확인" }));

    expect(document.activeElement).toBe(opener);
  });

  it("TD-013: 열린 상태로 onClose 클로저가 바뀌어도 Escape는 최신 콜백을 호출한다", async () => {
    const onCloseWithId = vi.fn();
    render(<ChangingOnCloseHarness onCloseWithId={onCloseWithId} />);

    await userEvent.click(screen.getByRole("button", { name: "id 변경" }));
    await userEvent.click(screen.getByRole("button", { name: "id 변경" }));
    await userEvent.keyboard("{Escape}");

    expect(onCloseWithId).toHaveBeenCalledExactlyOnceWith(3);
  });
});
