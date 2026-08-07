import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { StatusBadge } from "@/ui/primitives/status-badge";

describe("StatusBadge 프리미티브", () => {
  it("색상뿐 아니라 텍스트 라벨을 항상 렌더링한다", () => {
    render(<StatusBadge status="fresh" label="최신" />);
    expect(screen.getByText("최신")).toBeInTheDocument();
  });

  it("상태를 나타내는 점(dot)은 스크린리더에서 숨긴다(중복 정보이므로 라벨 텍스트로 충분)", () => {
    const { container } = render(<StatusBadge status="error" label="오류" />);
    const dot = container.querySelector("[aria-hidden='true']");
    expect(dot).toBeInTheDocument();
  });
});
