import { describe, expect, it } from "vitest";
import { act, fireEvent, render, screen } from "@testing-library/react";
import { useRovingGrid } from "@/ui/primitives/use-roving-grid";

// fireEvent.focus는 focus 이벤트만 발화할 뿐 jsdom의 document.activeElement를 실제로
// 옮기지 않는다 — 훅의 onFocus 핸들러(상태 갱신)는 반응하지만 실제 포커스 이동이
// 필요한 단언(toHaveFocus)에는 쓸 수 없다. act로 감싼 진짜 .focus() 호출을 쓴다.
function focusButton(button: HTMLElement) {
  act(() => {
    button.focus();
  });
}

// M-9: itemCount만 알고 disabled 상태를 모르는 순수 인덱스 산술이라, 방향키가 비활성
// 셀에 tabIndex/focus를 얹으려다 desync가 났다(네이티브 disabled 버튼은 .focus()가
// no-op이라 DOM 포커스는 이전 셀에 남는데 roving 마커만 옮겨감). 활성 셀만 순회하는지
// 직접 검증한다.
function Grid({ count, disabledIndexes }: { count: number; disabledIndexes: number[] }) {
  const roving = useRovingGrid(count, (index) => disabledIndexes.includes(index));
  return (
    <div role="group" aria-label="격자" onKeyDown={roving.handleKeyDown}>
      {Array.from({ length: count }, (_, index) => (
        <button
          key={index}
          type="button"
          {...roving.getItemProps(index)}
          disabled={disabledIndexes.includes(index)}
        >
          {index}
        </button>
      ))}
    </div>
  );
}

describe("useRovingGrid의 비활성 셀 스킵(M-9)", () => {
  it("ArrowRight가 비활성 셀을 건너뛰고 다음 활성 셀로 이동한다", () => {
    render(<Grid count={5} disabledIndexes={[1, 2]} />);
    const buttons = screen.getAllByRole("button");
    focusButton(buttons[0]);

    fireEvent.keyDown(screen.getByRole("group"), { key: "ArrowRight" });

    expect(buttons[3]).toHaveFocus();
    expect(buttons[3]).toHaveAttribute("tabindex", "0");
  });

  it("ArrowLeft가 비활성 셀을 건너뛴다", () => {
    render(<Grid count={5} disabledIndexes={[2, 3]} />);
    const buttons = screen.getAllByRole("button");
    focusButton(buttons[4]);

    fireEvent.keyDown(screen.getByRole("group"), { key: "ArrowLeft" });

    expect(buttons[1]).toHaveFocus();
  });

  it("Home은 맨 앞이 비활성이면 첫 활성 셀로 이동한다", () => {
    render(<Grid count={5} disabledIndexes={[0, 1]} />);
    const buttons = screen.getAllByRole("button");
    focusButton(buttons[4]);

    fireEvent.keyDown(screen.getByRole("group"), { key: "Home" });

    expect(buttons[2]).toHaveFocus();
  });

  it("End는 맨 끝이 비활성이면 마지막 활성 셀로 이동한다", () => {
    render(<Grid count={5} disabledIndexes={[3, 4]} />);
    const buttons = screen.getAllByRole("button");
    focusButton(buttons[0]);

    fireEvent.keyDown(screen.getByRole("group"), { key: "End" });

    expect(buttons[2]).toHaveFocus();
  });

  it("이동 방향에 활성 셀이 전혀 없으면 포커스를 유지한다(그리드 이탈 방지)", () => {
    render(<Grid count={5} disabledIndexes={[3, 4]} />);
    const buttons = screen.getAllByRole("button");
    focusButton(buttons[2]);

    // 그룹이 아니라 실제 포커스된 버튼에서 이벤트를 발화한다(실제 브라우저와 동일하게 —
    // 키 이벤트는 포커스된 요소에서 나서 그룹까지 버블링된다). 그룹에 직접 dispatch하면
    // jsdom에서 활성 요소가 아닌 노드로 이벤트를 보내는 셈이라 포커스 상태가 흔들린다.
    fireEvent.keyDown(buttons[2], { key: "ArrowRight" });

    expect(buttons[2]).toHaveFocus();
  });

  // 방향키 없이 외부 상태 변화로 현재 tabIndex 셀이 비활성화되는 실제 시나리오
  // (AnalysisNumberPicker에서 6개 선택 완료 시 나머지가 한꺼번에 비활성화됨).
  it("탭 스톱이 있던 셀이 외부 상태 변화로 비활성화되면 탭 진입점을 잃지 않는다", () => {
    const { rerender } = render(<Grid count={5} disabledIndexes={[]} />);
    const buttons = screen.getAllByRole("button");
    focusButton(buttons[2]);
    expect(buttons[2]).toHaveAttribute("tabindex", "0");

    // 리렌더로 인덱스 2가 비활성화된다(포커스는 그대로 buttons[2]에 남아있어도 —
    // 실제로는 브라우저가 비활성화된 요소의 포커스를 해제하지만, 이 테스트는
    // tabIndex 마커의 재배치 자체를 검증한다).
    rerender(<Grid count={5} disabledIndexes={[2]} />);

    const stillTabbable = buttons.filter((button) => button.getAttribute("tabindex") === "0");
    expect(stillTabbable).toHaveLength(1);
    expect(stillTabbable[0]).not.toBe(buttons[2]);
  });
});
