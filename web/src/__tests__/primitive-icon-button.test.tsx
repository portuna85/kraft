import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { IconButton } from "@/ui/primitives/icon-button";

describe("IconButton 프리미티브", () => {
  it("시각적 라벨이 없으므로 aria-label이 접근 가능한 이름이 된다", () => {
    render(<IconButton aria-label="닫기" variant="quiet" icon={<span>×</span>} />);
    expect(screen.getByRole("button", { name: "닫기" })).toBeInTheDocument();
  });

  it("클릭 시 onClick을 호출한다", () => {
    const onClick = vi.fn();
    render(<IconButton aria-label="닫기" variant="quiet" icon={<span>×</span>} onClick={onClick} />);
    screen.getByRole("button", { name: "닫기" }).click();
    expect(onClick).toHaveBeenCalledOnce();
  });

  it("아이콘은 스크린리더에서 숨긴다(중복 정보 — aria-label로 충분)", () => {
    const { container } = render(<IconButton aria-label="닫기" variant="quiet" icon={<span>×</span>} />);
    expect(container.querySelector("[aria-hidden='true']")).toBeInTheDocument();
  });
});
