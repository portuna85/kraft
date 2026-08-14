import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { RecommendationDisclaimer } from "./disclaimer";

describe("RecommendationDisclaimer", () => {
  it("확률 고지 제목·확률 문구·구매 책임 문구를 렌더한다 (법적 요구)", () => {
    render(<RecommendationDisclaimer />);

    expect(screen.getByText("추천 번호에 대해 알아두세요")).toBeInTheDocument();
    expect(screen.getByText(/8,145,060분의 1/)).toBeInTheDocument();
    expect(screen.getByText(/구매는 본인의 판단과 책임으로/)).toBeInTheDocument();
  });
});
