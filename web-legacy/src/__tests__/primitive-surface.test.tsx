import { describe, expect, it } from "vitest";
import { render } from "@testing-library/react";
import { Surface } from "@/ui/primitives/surface";

describe("Surface 프리미티브", () => {
  it("children을 렌더링한다", () => {
    const { getByText } = render(<Surface level={1}>내용</Surface>);
    expect(getByText("내용")).toBeInTheDocument();
  });

  it.each([1, 2, 3] as const)("level=%i에 대응하는 클래스를 붙인다", (level) => {
    const { container } = render(<Surface level={level}>x</Surface>);
    const el = container.firstElementChild as HTMLElement;
    expect(el.className).toContain(`level${level}`);
  });
});
