import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { ErrorState } from "@/ui/primitives/error-state";

describe("ErrorState 프리미티브", () => {
  it("role=alert로 렌더링한다", () => {
    render(<ErrorState title="조회에 실패했습니다" />);
    expect(screen.getByRole("alert")).toHaveTextContent("조회에 실패했습니다");
  });

  it("retry가 있으면 클릭 시 onClick을 호출한다", () => {
    const onClick = vi.fn();
    render(<ErrorState title="실패" retry={{ label: "다시 시도", onClick }} />);
    screen.getByRole("button", { name: "다시 시도" }).click();
    expect(onClick).toHaveBeenCalledOnce();
  });

  // FE-008: 인라인 자리마다 <p> + 버튼을 손으로 조립해 문구·마크업·복구 수단이 제각각이었다.
  describe("인라인 변형", () => {
    it("블록과 같은 계약으로 제목·설명·재시도를 제공한다", () => {
      const onClick = vi.fn();
      render(
        <ErrorState
          variant="inline"
          title="불러오지 못했습니다"
          description="이전 결과를 보여주고 있습니다"
          retry={{ label: "다시 시도", onClick }}
        />
      );

      const alert = screen.getByRole("alert");
      expect(alert).toHaveTextContent("불러오지 못했습니다");
      expect(alert).toHaveTextContent("이전 결과를 보여주고 있습니다");

      screen.getByRole("button", { name: "다시 시도" }).click();
      expect(onClick).toHaveBeenCalledOnce();
    });

    it("문단 흐름을 유지하도록 p로 렌더링한다", () => {
      render(<ErrorState variant="inline" title="실패" />);
      expect(screen.getByRole("alert").tagName).toBe("P");
    });

    it("재시도가 없으면 버튼을 만들지 않는다", () => {
      render(<ErrorState variant="inline" title="실패" />);
      expect(screen.queryByRole("button")).not.toBeInTheDocument();
    });
  });
});
