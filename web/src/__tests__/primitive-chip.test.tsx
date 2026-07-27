import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { Chip } from "@/ui/primitives/chip";

describe("Chip 프리미티브", () => {
  it("selected 상태를 aria-pressed로 노출한다", () => {
    render(<Chip selected>추천 공유</Chip>);
    expect(screen.getByRole("button", { name: "추천 공유" })).toHaveAttribute("aria-pressed", "true");
  });

  it("클릭 시 onToggle을 호출한다", () => {
    const onToggle = vi.fn();
    render(<Chip onToggle={onToggle}>회차 분석</Chip>);
    screen.getByRole("button", { name: "회차 분석" }).click();
    expect(onToggle).toHaveBeenCalledOnce();
  });
});
