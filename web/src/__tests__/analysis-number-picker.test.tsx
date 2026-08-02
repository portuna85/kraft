import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import {
  AnalysisNumberPicker,
  parseSelectedNumbers,
  toInputValue,
} from "@/app/analysis/analysis-number-picker";

describe("분석 번호 선택판 파서", () => {
  it("쉼표·공백을 모두 구분자로 받는다", () => {
    expect(parseSelectedNumbers("3, 11 19,28  34,42")).toEqual([3, 11, 19, 28, 34, 42]);
  });

  it("범위를 벗어난 값과 중복은 무시한다", () => {
    expect(parseSelectedNumbers("0, 3, 46, 3, 45")).toEqual([3, 45]);
  });

  it("입력 순서를 유지한다", () => {
    expect(parseSelectedNumbers("42, 3")).toEqual([42, 3]);
  });

  it("빈 입력은 빈 배열이다", () => {
    expect(parseSelectedNumbers("   ")).toEqual([]);
  });

  it("문자열로 되돌릴 때 사람이 읽는 형식을 쓴다", () => {
    expect(toInputValue([3, 11])).toBe("3, 11");
  });
});

// FE-037: 예전에는 쉼표 구분 문자열만 받아 모바일 입력 오류율이 높았다.
describe("분석 번호 선택판", () => {
  it("번호를 누르면 입력 문자열에 반영된다", () => {
    const onChange = vi.fn();
    render(<AnalysisNumberPicker input="" onChange={onChange} />);

    fireEvent.click(screen.getByRole("button", { name: "번호 7" }));

    expect(onChange).toHaveBeenCalledWith("7");
  });

  it("이미 선택한 번호를 다시 누르면 해제한다", () => {
    const onChange = vi.fn();
    render(<AnalysisNumberPicker input="3, 7, 11" onChange={onChange} />);

    fireEvent.click(screen.getByRole("button", { name: "번호 7, 선택" }));

    expect(onChange).toHaveBeenCalledWith("3, 11");
  });

  it("텍스트로 입력한 번호가 선택 상태로 보인다", () => {
    render(<AnalysisNumberPicker input="3, 11" onChange={() => {}} />);

    expect(screen.getByRole("button", { name: "번호 3, 선택" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: "번호 4" })).toHaveAttribute("aria-pressed", "false");
  });

  it("6개를 채우면 남은 번호를 누를 수 없고 선택 수를 알린다", () => {
    render(<AnalysisNumberPicker input="1, 2, 3, 4, 5, 6" onChange={() => {}} />);

    expect(screen.getByRole("status")).toHaveTextContent("6/6개 선택");
    expect(screen.getByRole("button", { name: "번호 7" })).toBeDisabled();
    // 이미 고른 번호는 해제할 수 있어야 한다 — 그러지 않으면 되돌릴 방법이 없다.
    expect(screen.getByRole("button", { name: "번호 1, 선택" })).toBeEnabled();
  });

  it("제출 중에는 번호판 전체가 비활성이다", () => {
    render(<AnalysisNumberPicker input="3" onChange={() => {}} disabled />);

    expect(screen.getByRole("button", { name: "번호 3, 선택" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "번호 4" })).toBeDisabled();
  });
});
