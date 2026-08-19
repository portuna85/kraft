import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { ComponentProps } from "react";

import { SiteFooter } from "./site-footer";

// KF-20(docs/improvement.md): prefetch={false}는 렌더된 DOM에 표준 속성으로
// 남지 않아(Link 내부 라우터 동작만 바꾼다) 렌더 결과만으로는 검증할 수 없다 —
// next/link를 목으로 바꿔 전달되는 prop을 data-prefetch로 노출한다.
vi.mock("next/link", () => ({
  default: ({
    href,
    prefetch,
    children,
    ...rest
  }: ComponentProps<"a"> & { prefetch?: boolean }) => (
    <a
      href={typeof href === "string" ? href : undefined}
      data-prefetch={String(prefetch)}
      {...rest}
    >
      {children}
    </a>
  ),
}));

describe("SiteFooter", () => {
  it("KF-20: 모든 푸터 링크가 prefetch=false다(저의도)", () => {
    render(<SiteFooter />);

    const links = screen.getAllByRole("link");
    expect(links.length).toBeGreaterThan(0);
    for (const link of links) {
      expect(link).toHaveAttribute("data-prefetch", "false");
    }
  });
});
