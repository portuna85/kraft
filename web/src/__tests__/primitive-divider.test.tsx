import { describe, expect, it } from "vitest";
import { render } from "@testing-library/react";
import { Divider } from "@/ui/primitives/divider";

describe("Divider 프리미티브", () => {
  it("기본값(horizontal)은 hr 요소로 렌더링한다", () => {
    const { container } = render(<Divider />);
    expect(container.querySelector("hr")).toBeInTheDocument();
  });

  it("orientation=vertical은 separator role로 렌더링한다", () => {
    const { getByRole } = render(<Divider orientation="vertical" />);
    expect(getByRole("separator")).toHaveAttribute("aria-orientation", "vertical");
  });
});
