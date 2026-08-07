import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { FAQ_ITEMS } from "@/lib/csp-inline-scripts";
import { FaqContent } from "@/app/info/[slug]/page";

describe("FaqContent", () => {
  it("각 FAQ 질문을 페이지 h1의 바로 아래 레벨인 h2로 렌더링한다", () => {
    render(<FaqContent />);

    expect(screen.getAllByRole("heading", { level: 2 })).toHaveLength(FAQ_ITEMS.length);
    FAQ_ITEMS.forEach((item) => {
      expect(screen.getByRole("heading", { name: item.question, level: 2 })).toBeInTheDocument();
    });
  });
});
