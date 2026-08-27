import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { SavedHistoryNav } from "./saved-history-nav";

let mockPathname = "/saved";

vi.mock("next/navigation", () => ({
  usePathname: () => mockPathname,
}));

describe("SavedHistoryNav", () => {
  beforeEach(() => {
    mockPathname = "/saved";
  });

  it("두 탭을 모두 링크로 렌더한다", () => {
    render(<SavedHistoryNav />);

    expect(screen.getByRole("link", { name: "저장한 번호" })).toHaveAttribute("href", "/saved");
    expect(screen.getByRole("link", { name: "추천 이력" })).toHaveAttribute(
      "href",
      "/recommend/history",
    );
  });

  it("/saved에서는 저장한 번호 탭만 현재다", () => {
    mockPathname = "/saved";
    render(<SavedHistoryNav />);

    expect(screen.getByRole("link", { name: "저장한 번호" })).toHaveAttribute(
      "aria-current",
      "page",
    );
    expect(screen.getByRole("link", { name: "추천 이력" })).not.toHaveAttribute("aria-current");
  });

  it("/recommend/history에서는 추천 이력 탭만 현재다", () => {
    mockPathname = "/recommend/history";
    render(<SavedHistoryNav />);

    expect(screen.getByRole("link", { name: "추천 이력" })).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("link", { name: "저장한 번호" })).not.toHaveAttribute("aria-current");
  });
});
