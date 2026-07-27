import { describe, expect, it } from "vitest";
import { render } from "@testing-library/react";
import { EditorialPhoto } from "@/ui/domain/editorial-photo";

describe("EditorialPhoto 계약", () => {
  it("실제 자산 없이도 aspect-ratio를 예약한 자리표시자를 렌더링한다(CLS 방지)", () => {
    const { container } = render(<EditorialPhoto slug="home-recommendation-journal" alt="" aspectRatio="16 / 9" />);
    const el = container.firstElementChild as HTMLElement;
    expect(el).toHaveStyle({ aspectRatio: "16 / 9" });
    expect(el).toHaveAttribute("data-editorial-photo-slug", "home-recommendation-journal");
  });
});
