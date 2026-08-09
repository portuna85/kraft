import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { MatchResultBadge } from "./match-result-badge";

describe("회차 대조 결과 표시", () => {
  it("등수를 텍스트로 보여준다 — 색만으로 전달하지 않는다", () => {
    render(<MatchResultBadge prizeTier="1등" matchedCount={6} bonusMatch={false} />);
    expect(screen.getByText("1등")).toBeInTheDocument();
  });

  it("일치 개수와 보너스 일치 여부를 함께 보여준다", () => {
    render(<MatchResultBadge prizeTier="2등" matchedCount={5} bonusMatch={true} />);
    expect(screen.getByText("5개 일치 · 보너스 일치")).toBeInTheDocument();
  });

  it("보너스가 안 맞았으면 보너스 문구를 붙이지 않는다", () => {
    render(<MatchResultBadge prizeTier="낙첨" matchedCount={2} bonusMatch={false} />);
    expect(screen.getByText("2개 일치")).toBeInTheDocument();
  });
});
