import { describe, expect, it } from "vitest";

import {
  currentNavHref,
  isRouteCurrent,
  type NavItem,
  PRIMARY_NAV,
  TAB_BAR_ITEMS,
} from "./nav-items";

/**
 * RSP-23 / RSP-24(docs/improvement.md): "현재 위치" 모델이 라우트 계층을 안다.
 *
 * 예전에는 홈 외 모든 경로에 경계 없는 `pathname.startsWith(href)`를 썼고 각
 * 링크가 그 boolean을 독립적으로 계산했다. 두 가지가 동시에 깨졌다.
 *   - **부모/자식 동시 현재**: `/recommend`와 `/recommend/history`가 같은 메뉴에
 *     함께 있어 `/recommend/history`에서 둘 다 현재가 됐다.
 *   - **하위 화면의 현재 소실**: 모바일 탭에는 `/data`만 있고 통계 상세는
 *     `/frequency`·`/stats`·`/companion`·`/analysis`라는 별도 최상위 URL이라
 *     어느 탭도 현재가 되지 않았다.
 *
 * 경계 인식 prefix 판정은 이 저장소에 이미 있었다 — `shared/config/routes.ts`의
 * `isSessionScopedPath`가 `p === prefix || p.startsWith(prefix + "/")`를 쓴다.
 * `isRouteCurrent`도 같은 형태로 맞췄다.
 */

/**
 * 메뉴 집합에서 현재로 판정되는 항목들. 계약상 0개 또는 정확히 1개여야 한다.
 *
 * 컴포넌트가 쓰는 것과 **같은** 선택자를 통과시킨다 — 항목별 boolean을 여기서
 * 따로 세면 테스트가 실제 렌더 동작과 다른 것을 재게 된다.
 */
function currentItems(items: readonly NavItem[], pathname: string) {
  const href = currentNavHref(items, pathname);
  return items.filter((item) => item.href === href).map((item) => item.label);
}

const PRIMARY_NAV_ITEMS = PRIMARY_NAV.flatMap((group) => group.items);

describe("isRouteCurrent — 경로 경계", () => {
  it("홈은 정확히 일치할 때만 현재다", () => {
    expect(isRouteCurrent("/", "/")).toBe(true);
    expect(isRouteCurrent("/", "/recommend")).toBe(false);
  });

  it("자기 자신과 하위 경로를 현재로 본다", () => {
    expect(isRouteCurrent("/community", "/community")).toBe(true);
    expect(isRouteCurrent("/community", "/community/posts/1")).toBe(true);
  });

  // RSP-23: 문자열 우연 일치를 막는다. 예전 판정은 `/data`가 `/database`를
  // 현재로 봤다 — 그런 라우트가 아직 없을 뿐 계약이 잘못돼 있었다.
  it("RSP-23: 세그먼트 경계를 넘는 우연한 접두어 일치를 현재로 보지 않는다", () => {
    expect(isRouteCurrent("/data", "/database")).toBe(false);
    expect(isRouteCurrent("/saved", "/saved-items")).toBe(false);
    expect(isRouteCurrent("/recommend", "/recommendation")).toBe(false);
  });
});

describe("RSP-23: 데스크톱 주요 메뉴에서 현재 항목은 0개 또는 정확히 1개다", () => {
  const CASES: { pathname: string; expected: string[] }[] = [
    { pathname: "/recommend", expected: ["번호 추천"] },
    // 핵심 케이스: 예전에는 `/recommend`와 `/recommend/history`가 동시에 현재였다.
    { pathname: "/recommend/history", expected: ["추천 이력"] },
    { pathname: "/community", expected: ["커뮤니티"] },
    { pathname: "/community/posts/1", expected: ["커뮤니티"] },
    { pathname: "/community/write", expected: ["커뮤니티"] },
    { pathname: "/data", expected: ["데이터 개요"] },
    { pathname: "/frequency", expected: ["번호별 출현"] },
    { pathname: "/stats", expected: ["당첨 패턴"] },
    { pathname: "/companion", expected: ["함께 나온 번호"] },
    { pathname: "/analysis", expected: ["내 조합 진단"] },
    { pathname: "/info/data-source", expected: ["데이터 출처"] },
    { pathname: "/info/methodology", expected: ["방법론"] },
    // "보관함"은 4대 축 밖의 유틸리티라 PRIMARY_NAV에서 빠졌다 — utility nav
    // (site-header.tsx의 SavedLink)로 옮겼다.
    { pathname: "/saved", expected: [] },
    // 주요 메뉴에 없는 라우트는 아무것도 현재가 아니다.
    { pathname: "/", expected: [] },
    { pathname: "/status", expected: [] },
    { pathname: "/info/faq", expected: [] },
  ];

  for (const { pathname, expected } of CASES) {
    it(`${pathname} -> ${expected.length === 0 ? "(없음)" : expected.join(", ")}`, () => {
      expect(currentItems(PRIMARY_NAV_ITEMS, pathname)).toEqual(expected);
    });
  }
});

describe("RSP-24: 모바일 탭에서 현재 탭은 0개 또는 정확히 1개다", () => {
  const CASES: { pathname: string; expected: string[] }[] = [
    { pathname: "/", expected: ["홈"] },
    { pathname: "/recommend", expected: ["추천"] },
    { pathname: "/recommend/history", expected: ["추천"] },
    { pathname: "/data", expected: ["데이터"] },
    // 핵심 케이스: 데이터 하위 화면은 별도 최상위 URL이라 alias 없이는 현재 탭이 사라진다.
    { pathname: "/frequency", expected: ["데이터"] },
    { pathname: "/stats", expected: ["데이터"] },
    { pathname: "/companion", expected: ["데이터"] },
    { pathname: "/analysis", expected: ["데이터"] },
    { pathname: "/community", expected: ["커뮤니티"] },
    { pathname: "/community/posts/1", expected: ["커뮤니티"] },
    { pathname: "/community/posts/1/edit", expected: ["커뮤니티"] },
    { pathname: "/community/write", expected: ["커뮤니티"] },
    { pathname: "/saved", expected: ["보관함"] },
    // 탭 체계 밖의 라우트는 현재 탭이 없는 것이 옳다.
    { pathname: "/status", expected: [] },
    { pathname: "/info/faq", expected: [] },
    { pathname: "/ops", expected: [] },
  ];

  for (const { pathname, expected } of CASES) {
    it(`${pathname} -> ${expected.length === 0 ? "(없음)" : expected.join(", ")}`, () => {
      expect(currentItems(TAB_BAR_ITEMS, pathname)).toEqual(expected);
    });
  }
});
