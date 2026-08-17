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

  // I-26: dialog.test.tsx와 같은 이유 — 열려 있는 동안 배경 스크롤이 잠기지 않으면
  // 백드롭 위에서 휠을 굴릴 때 뒷 페이지가 스크롤된다. Drawer에는 이 잠금이 아예
  // 없었다(use-scroll-lock.ts로 Dialog와 공유하도록 고쳤다).
  it("열려 있는 동안 배경 스크롤을 잠그고 닫히면 되돌린다", () => {
    const { rerender } = render(
      <Drawer open={false} onClose={vi.fn()} title="제목">
        내용
      </Drawer>,
    );
    expect(document.body.style.overflow).not.toBe("hidden");

    rerender(
      <Drawer open onClose={vi.fn()} title="제목">
        내용
      </Drawer>,
    );
    expect(document.body.style.overflow).toBe("hidden");

    rerender(
      <Drawer open={false} onClose={vi.fn()} title="제목">
        내용
      </Drawer>,
    );
    expect(document.body.style.overflow).not.toBe("hidden");
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

  describe("바텀시트 스와이프", () => {
    /** jsdom에는 포인터 캡처가 없다. 제스처 판정만 검증하면 되므로 no-op으로 채운다. */
    function renderBottomDrawer(onClose: () => void) {
      render(
        <Drawer open onClose={onClose} title="제목" side="bottom">
          내용
        </Drawer>,
      );

      const handle = document.querySelector<HTMLDivElement>('[aria-hidden="true"][class]');
      if (handle === null) throw new Error("드래그 핸들을 찾지 못했다");
      handle.setPointerCapture = () => undefined;
      return handle;
    }

    function swipe(handle: HTMLElement, deltaY: number) {
      handle.dispatchEvent(
        new PointerEvent("pointerdown", { bubbles: true, clientY: 0, pointerId: 1 }),
      );
      handle.dispatchEvent(
        new PointerEvent("pointerup", { bubbles: true, clientY: deltaY, pointerId: 1 }),
      );
    }

    it("아래로 충분히 끌면 닫는다", () => {
      const onClose = vi.fn();
      swipe(renderBottomDrawer(onClose), 120);
      expect(onClose).toHaveBeenCalledTimes(1);
    });

    it("조금만 움직이면 닫지 않는다 — 스크롤과 구분한다", () => {
      const onClose = vi.fn();
      swipe(renderBottomDrawer(onClose), 20);
      expect(onClose).not.toHaveBeenCalled();
    });

    it("위로 끄는 동작으로는 닫지 않는다", () => {
      const onClose = vi.fn();
      swipe(renderBottomDrawer(onClose), -120);
      expect(onClose).not.toHaveBeenCalled();
    });

    it("옆에서 나오는 드로어에는 핸들이 없다", () => {
      render(
        <Drawer open onClose={vi.fn()} title="제목" side="right">
          내용
        </Drawer>,
      );
      expect(document.querySelector('[aria-hidden="true"][class]')).toBeNull();
    });
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
