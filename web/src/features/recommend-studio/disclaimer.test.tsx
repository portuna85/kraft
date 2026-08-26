import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";

import { RecommendationDisclaimer } from "./disclaimer";

describe("RecommendationDisclaimer", () => {
  it("확률 고지 제목·확률 문구는 기본으로 보인다 (법적 요구)", () => {
    render(<RecommendationDisclaimer />);

    expect(screen.getByText("추천 번호에 대해 알아두세요")).toBeInTheDocument();
    expect(screen.getByText(/8,145,060분의 1/)).toBeInTheDocument();
  });

  it("구매 책임 문구는 기본으로 접혀 있고 '자세히 보기'로 펼칠 수 있다", async () => {
    const user = userEvent.setup();
    render(<RecommendationDisclaimer />);

    const detail = screen.getByText(/구매는 본인의 판단과 책임으로/);
    expect(detail.closest("[hidden]")).not.toBeNull();

    const toggle = screen.getByRole("button", { name: "자세히 보기" });
    expect(toggle).toHaveAttribute("aria-expanded", "false");

    await user.click(toggle);

    expect(toggle).toHaveAttribute("aria-expanded", "true");
    expect(screen.getByRole("button", { name: "간단히 보기" })).toBeInTheDocument();
    expect(detail.closest("[hidden]")).toBeNull();
  });
});
