import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { Badge } from "@/ui/primitives/badge";
import type { BadgeTone } from "@/ui/primitives/contracts";

describe("Badge 프리미티브", () => {
  it("children 텍스트를 렌더링한다", () => {
    render(<Badge tone="neutral">라벨</Badge>);
    expect(screen.getByText("라벨")).toBeInTheDocument();
  });

  it.each(["neutral", "brand", "success", "warning", "danger"] as const satisfies readonly BadgeTone[])(
    "tone=%s에 맞는 클래스를 붙인다",
    (tone) => {
      const { container } = render(<Badge tone={tone}>x</Badge>);
      expect((container.firstElementChild as HTMLElement).className).toContain(tone);
    }
  );
});
