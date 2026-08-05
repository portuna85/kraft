import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { Footer } from "@/components/footer";

describe("공용 푸터", () => {
  it("정보 링크 9개를 올바른 href로 렌더링한다", () => {
    render(<Footer />);

    const expected: Array<[string, string]> = [
      ["데이터 출처", "/info/data-source"],
      ["분석 기준", "/info/methodology"],
      ["FAQ", "/info/faq"],
      ["개인정보처리방침", "/info/privacy"],
      ["이용약관", "/info/terms"],
      ["건전한 이용", "/info/responsible-play"],
      ["커뮤니티 이용규칙", "/info/community-guidelines"],
      ["문의하기", "/info/contact"],
      ["운영자 소개", "/info/about"],
    ];

    for (const [label, href] of expected) {
      expect(screen.getByRole("link", { name: label })).toHaveAttribute("href", href);
    }
  });

  it("데이터 출처·면책 문구를 보여준다", () => {
    render(<Footer />);

    expect(screen.getByText(/동행복권 공식 데이터를 기준으로 제공/)).toBeInTheDocument();
    expect(screen.getByText(/당첨을 보장하지 않으며/)).toBeInTheDocument();
  });
});
