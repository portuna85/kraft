import { act, fireEvent, render, renderHook, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { KeyboardEvent } from "react";

import { useRovingGrid } from "./use-roving-grid";

function Grid({ disabled = [] }: { disabled?: number[] }) {
  const { activeIndex, onKeyDown, getCellProps } = useRovingGrid({
    itemCount: 6,
    columns: 3,
    isDisabled: (index) => disabled.includes(index),
  });
  return (
    <div role="grid" aria-label="번호" onKeyDown={onKeyDown}>
      <output aria-label="활성 인덱스">{activeIndex}</output>
      {Array.from({ length: 6 }, (_, index) => (
        <button key={index} type="button" role="gridcell" {...getCellProps(index)}>
          {index}
        </button>
      ))}
    </div>
  );
}

describe("useRovingGrid", () => {
  it("활성 셀 하나만 탭 정지점으로 둔다", () => {
    render(<Grid />);

    const cells = screen.getAllByRole("gridcell");
    expect(cells[0]).toHaveAttribute("tabindex", "0");
    for (const cell of cells.slice(1)) {
      expect(cell).toHaveAttribute("tabindex", "-1");
    }
  });

  it("화살표 이동 시 비활성 셀을 건너뛴다", () => {
    render(<Grid disabled={[1]} />);
    const grid = screen.getByRole("grid", { name: "번호" });

    fireEvent.keyDown(grid, { key: "ArrowRight" });

    expect(screen.getByRole("status", { name: "활성 인덱스" })).toHaveTextContent("2");
    expect(screen.getAllByRole("gridcell")[2]).toHaveAttribute("tabindex", "0");
  });

  it("Home과 End가 양 끝의 활성 셀로 이동하고 범위 밖 이동은 무시한다", () => {
    render(<Grid disabled={[5]} />);
    const grid = screen.getByRole("grid", { name: "번호" });

    fireEvent.keyDown(grid, { key: "End" });
    expect(screen.getByRole("status", { name: "활성 인덱스" })).toHaveTextContent("4");
    fireEvent.keyDown(grid, { key: "ArrowDown" });
    expect(screen.getByRole("status", { name: "활성 인덱스" })).toHaveTextContent("4");
    fireEvent.keyDown(grid, { key: "Home" });
    expect(screen.getByRole("status", { name: "활성 인덱스" })).toHaveTextContent("0");
  });

  it("셀에 포커스하면 그 셀이 새 탭 정지점이 된다", () => {
    render(<Grid />);

    fireEvent.focus(screen.getAllByRole("gridcell")[3]!);

    expect(screen.getByRole("status", { name: "활성 인덱스" })).toHaveTextContent("3");
    expect(screen.getAllByRole("gridcell")[3]).toHaveAttribute("tabindex", "0");
  });

  // I-13 회귀 테스트: activeIndex(탭 정지점) 변경만으로는 이 버그를 못 잡는다 — 위
  // 테스트들이 전부 통과하는 상태에서도 실제 document.activeElement는 그대로였다.
  // fireEvent.focus는 합성 이벤트만 보내고 실제 DOM 포커스는 옮기지 않으므로,
  // 여기서는 반드시 실제 .focus()로 시작해 화살표 후 document.activeElement를
  // 직접 확인한다.
  it("화살표로 이동하면 실제 브라우저 포커스도 함께 옮긴다", () => {
    render(<Grid />);
    const grid = screen.getByRole("grid", { name: "번호" });
    const cells = screen.getAllByRole("gridcell");

    cells[0]!.focus();
    expect(document.activeElement).toBe(cells[0]);

    fireEvent.keyDown(grid, { key: "ArrowRight" });

    expect(document.activeElement).toBe(cells[1]);
    expect(screen.getByRole("status", { name: "활성 인덱스" })).toHaveTextContent("1");
  });

  it("포커스가 그리드 밖에 있을 때는 activeIndex 변경이 포커스를 뺏지 않는다", () => {
    render(<Grid />);
    // 마운트 직후 activeIndex 기본값(0)이 셀 0을 강제로 포커스하면 안 된다 —
    // 그리드가 화면에 나타나는 순간 사용자가 보던 곳에서 포커스가 튀는 부작용이 된다.
    expect(document.activeElement).not.toBe(screen.getAllByRole("gridcell")[0]);
  });

  // KF-26(FE-OPT-42, docs/improvement.md) 회귀 테스트: getCellProps 자체를
  // useCallback으로 감싸는 것만으로는 부족했다 — 반환값의 ref 콜백이 매 렌더
  // 새 클로저라 activeIndex가 바뀔 때마다(화살표 키 이동) React가 45개 셀
  // 전부 ref를 detach/reattach했다. 인덱스별 ref 콜백 정체성이 activeIndex
  // 변경 전후로 안정적인지 직접 확인한다.
  it("activeIndex가 바뀌어도 각 셀의 ref 콜백 정체성은 안정적으로 유지된다", () => {
    const { result } = renderHook(() => useRovingGrid({ itemCount: 6, columns: 3 }));

    const refsBefore = Array.from(
      { length: 6 },
      (_, index) => result.current.getCellProps(index).ref,
    );

    act(() => {
      result.current.onKeyDown({
        key: "ArrowRight",
        preventDefault: () => {},
      } as KeyboardEvent<HTMLElement>);
    });
    const refsAfter = Array.from(
      { length: 6 },
      (_, index) => result.current.getCellProps(index).ref,
    );

    for (let index = 0; index < 6; index += 1) {
      expect(refsAfter[index]).toBe(refsBefore[index]);
    }
  });
});
