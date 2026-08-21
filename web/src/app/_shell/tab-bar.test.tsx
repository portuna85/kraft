import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { TabBar } from "./tab-bar";

let mockPathname = "/";

vi.mock("next/navigation", () => ({
  usePathname: () => mockPathname,
}));

/**
 * RSP-24(docs/improvement.md): 모바일 탭바에는 지금까지 테스트가 없었다.
 *
 * 렌더 레벨에서만 드러나는 비대칭이 하나 있다 — 인디케이터는 `findIndex`로 **첫
 * 매치 하나**를 고르는데(`tab-bar.tsx:16`), 각 링크는 `isRouteCurrent`를 **독립적으로
 * 재계산한다**(`:30`). 두 탭이 동시에 매치되면 `aria-current="page"`는 둘이 되는데
 * 캡슐 인디케이터는 하나만 움직여 시각 표시와 보조 기술이 어긋난다. 그래서 이
 * 스펙은 `aria-current` 개수와 `data-active-index`를 **함께** 고정한다.
 */

/** 탭 인디케이터 CSS(shell.module.css:180,191-210)가 하드코딩한 탭 순서. */
const TAB_INDEX = { 홈: 0, 추천: 1, 데이터: 2, 커뮤니티: 3, 보관함: 4 } as const;

const CASES: { pathname: string; expected: keyof typeof TAB_INDEX | null }[] = [
  { pathname: "/", expected: "홈" },
  { pathname: "/recommend", expected: "추천" },
  { pathname: "/recommend/history", expected: "추천" },
  { pathname: "/data", expected: "데이터" },
  { pathname: "/frequency", expected: "데이터" },
  { pathname: "/stats", expected: "데이터" },
  { pathname: "/companion", expected: "데이터" },
  { pathname: "/analysis", expected: "데이터" },
  { pathname: "/community", expected: "커뮤니티" },
  { pathname: "/community/posts/1", expected: "커뮤니티" },
  { pathname: "/community/posts/1/edit", expected: "커뮤니티" },
  { pathname: "/community/write", expected: "커뮤니티" },
  { pathname: "/saved", expected: "보관함" },
  { pathname: "/status", expected: null },
  { pathname: "/info/faq", expected: null },
  { pathname: "/ops", expected: null },
];

describe("TabBar 현재 탭 계약", () => {
  beforeEach(() => {
    mockPathname = "/";
  });

  for (const { pathname, expected } of CASES) {
    it(`${pathname}에서 현재 탭은 ${expected ?? "(없음)"} 하나뿐이다`, () => {
      mockPathname = pathname;
      render(<TabBar />);

      const current = screen
        .getAllByRole("link")
        .filter((link) => link.getAttribute("aria-current") === "page");

      if (expected === null) {
        expect(current).toHaveLength(0);
      } else {
        expect(current.map((link) => link.textContent)).toEqual([expected]);
      }
    });

    it(`${pathname}의 data-active-index가 aria-current와 일치한다`, () => {
      mockPathname = pathname;
      render(<TabBar />);

      const nav = screen.getByRole("navigation", { name: "바로가기" });
      expect(nav.getAttribute("data-active-index")).toBe(
        expected === null ? null : String(TAB_INDEX[expected]),
      );
    });
  }
});
