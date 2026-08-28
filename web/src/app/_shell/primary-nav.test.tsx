import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { PrimaryNav } from "./primary-nav";

let mockPathname = "/";

vi.mock("next/navigation", () => ({
  usePathname: () => mockPathname,
}));

describe("PrimaryNav", () => {
  beforeEach(() => {
    mockPathname = "/";
  });

  // KF-25②(docs/improvement.md): 데스크톱 내비게이션이 현재 라우트를 노출하지
  // 않았다 — 모바일 TabBar에만 aria-current가 있어 뷰포트별로 동작이 달랐다.
  it("KF-25②: 현재 라우트에만 aria-current=page를 준다", () => {
    mockPathname = "/recommend";
    render(<PrimaryNav />);

    expect(screen.getByRole("link", { name: "번호 추천" })).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("link", { name: "커뮤니티" })).not.toHaveAttribute("aria-current");
  });

  it("하위 경로도 현재 라우트로 판정한다", () => {
    mockPathname = "/community/posts/1";
    render(<PrimaryNav />);

    expect(screen.getByRole("link", { name: "커뮤니티" })).toHaveAttribute("aria-current", "page");
  });

  it("PRIMARY_NAV 항목과 무관한 경로에서는 아무것도 현재로 표시하지 않는다", () => {
    mockPathname = "/";
    render(<PrimaryNav />);

    for (const link of screen.getAllByRole("link")) {
      expect(link).not.toHaveAttribute("aria-current");
    }
  });
});
