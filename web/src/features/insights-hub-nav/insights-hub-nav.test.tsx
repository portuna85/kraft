import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { InsightsHubNav } from "./insights-hub-nav";

let mockPathname = "/data";

vi.mock("next/navigation", () => ({
  usePathname: () => mockPathname,
}));

describe("InsightsHubNav", () => {
  beforeEach(() => {
    mockPathname = "/data";
  });

  it("다섯 탭을 모두 링크로 렌더한다", () => {
    render(<InsightsHubNav />);

    expect(screen.getByRole("link", { name: "개요" })).toHaveAttribute("href", "/data");
    expect(screen.getByRole("link", { name: "출현 통계" })).toHaveAttribute("href", "/frequency");
    expect(screen.getByRole("link", { name: "패턴 통계" })).toHaveAttribute("href", "/stats");
    expect(screen.getByRole("link", { name: "동반 출현" })).toHaveAttribute("href", "/companion");
    expect(screen.getByRole("link", { name: "데이터 출처" })).toHaveAttribute(
      "href",
      "/info/data-source",
    );
  });

  it("/frequency에서는 출현 통계 탭만 현재다", () => {
    mockPathname = "/frequency";
    render(<InsightsHubNav />);

    expect(screen.getByRole("link", { name: "출현 통계" })).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("link", { name: "개요" })).not.toHaveAttribute("aria-current");
    expect(screen.getByRole("link", { name: "패턴 통계" })).not.toHaveAttribute("aria-current");
    expect(screen.getByRole("link", { name: "동반 출현" })).not.toHaveAttribute("aria-current");
    expect(screen.getByRole("link", { name: "데이터 출처" })).not.toHaveAttribute("aria-current");
  });

  it("/stats에서는 패턴 통계 탭만 현재다", () => {
    mockPathname = "/stats";
    render(<InsightsHubNav />);

    expect(screen.getByRole("link", { name: "패턴 통계" })).toHaveAttribute("aria-current", "page");
  });

  it("/companion에서는 동반 출현 탭만 현재다", () => {
    mockPathname = "/companion";
    render(<InsightsHubNav />);

    expect(screen.getByRole("link", { name: "동반 출현" })).toHaveAttribute("aria-current", "page");
  });

  it("/info/data-source에서는 데이터 출처 탭만 현재다", () => {
    mockPathname = "/info/data-source";
    render(<InsightsHubNav />);

    expect(screen.getByRole("link", { name: "데이터 출처" })).toHaveAttribute(
      "aria-current",
      "page",
    );
  });
});
