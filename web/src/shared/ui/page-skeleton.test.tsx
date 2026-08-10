import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { PageSkeleton } from "./page-skeleton";

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
