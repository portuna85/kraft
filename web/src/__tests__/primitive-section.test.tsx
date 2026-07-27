import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { Section } from "@/ui/primitives/section";

describe("Section 프리미티브", () => {
  it("headingLevel에 맞는 heading role/level로 제목을 렌더링한다", () => {
    render(
      <Section headingLevel={3} title="섹션 제목">
        내용
      </Section>
    );
    const heading = screen.getByRole("heading", { level: 3, name: "섹션 제목" });
    expect(heading.tagName).toBe("H3");
  });

  it("children을 렌더링한다", () => {
    render(
      <Section headingLevel={2} title="제목">
        <p>본문</p>
      </Section>
    );
    expect(screen.getByText("본문")).toBeInTheDocument();
  });
});
