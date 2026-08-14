import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

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
});
