import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { ErrorState } from "@/ui/primitives/error-state";

describe("ErrorState 프리미티브", () => {
  it("role=alert로 렌더링한다", () => {
    render(<ErrorState title="조회에 실패했습니다" />);
    expect(screen.getByRole("alert")).toHaveTextContent("조회에 실패했습니다");
  });

  it("retry가 있으면 클릭 시 onClick을 호출한다(복구 행동 제시, §16.2)", () => {
    const onClick = vi.fn();
    render(<ErrorState title="실패" retry={{ label: "다시 시도", onClick }} />);
    screen.getByRole("button", { name: "다시 시도" }).click();
    expect(onClick).toHaveBeenCalledOnce();
  });
});
