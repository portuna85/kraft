import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { ListRowsSkeleton, PageSkeleton } from "./page-skeleton";

describe("ListRowsSkeleton", () => {
  it("KF-08(docs/improvement.md): 행 스켈레톤 8개를 그리고 로딩 중임을 알린다", () => {
    const { container } = render(<ListRowsSkeleton />);
    expect(container.firstElementChild).toHaveAttribute("aria-busy", "true");
    // PageSkeleton과 달리 eyebrow/title/description 헤더 블록 없이 행만 그린다 —
    // 부모 페이지가 이미 진짜 헤더를 렌더한 자리에 인라인으로 쓰기 위해서다.
    expect(container.querySelectorAll("span").length).toBe(16); // 행 8개 × (text+block)
  });
});

describe("PageSkeleton", () => {
  it("로딩 중임을 스크린리더에 알린다", () => {
    const { container } = render(<PageSkeleton />);
    expect(container.firstElementChild).toHaveAttribute("aria-busy", "true");
    expect(container.firstElementChild).toHaveAttribute("aria-label", "콘텐츠를 불러오는 중");
  });

  it.each([["home"], ["bars"], ["list"], ["generic"]] as const)(
    "%s variant가 렌더된다",
    (variant) => {
      const { container } = render(<PageSkeleton variant={variant} />);
      expect(container.querySelectorAll("span").length).toBeGreaterThan(0);
    },
  );
});
