import { ROUTES } from "@/shared/config/routes";

/**
 * 내비게이션 구성
 *
 * **URL은 전부 유지하고 라벨과 묶음만 바꾼다**(R-13). 전환 후 이탈률을 관찰하려면
 * 바뀐 것이 IA뿐이어야 한다 — URL까지 같이 바뀌면 원인을 분리할 수 없다.
 */

export type NavItem = {
  href: string;
  label: string;
  /**
   * RSP-23(docs/improvement.md): 이 항목이 하위 경로까지 흡수하는가.
   * `subtree`(기본)는 `/community`가 `/community/posts/1`을 포함한다는 뜻이고,
   * `exact`는 자기 자신에서만 현재가 된다 — 부모 항목이 같은 메뉴에 함께 있는
   * `추천 이력`이 그 경우다.
   */
  match?: "exact" | "subtree";
  /**
   * RSP-24(docs/improvement.md): 이 항목이 대표하는 별도 최상위 URL들.
   * 모바일 탭에는 `데이터` 하나뿐인데 통계 상세는 `/frequency`·`/stats`·
   * `/companion`·`/analysis`라는 독립 URL이라, alias가 없으면 그 화면들에서
   * 현재 탭이 통째로 사라진다.
   */
  aliases?: readonly string[];
};
export type NavGroup = { title: string; items: NavItem[] };

/**
 * KF-25②(docs/improvement.md): TabBar·PrimaryNav 둘 다 "현재 라우트인가"를
 * 판정해야 한다 — 한 곳에 두고 공유한다.
 *
 * RSP-23(docs/improvement.md): 판정을 경계 인식으로 바꿨다. 예전에는 경계 없는
 * `pathname.startsWith(href)`라 `/data`가 `/database`를 현재로 봤다. 형태는
 * `shared/config/routes.ts`의 `isSessionScopedPath`와 같다 — 그쪽이 이미 옳게
 * 하고 있었으므로 새로 발명하지 않는다.
 *
 * **이 함수는 항목 하나만 본다.** 메뉴 전체에서 "현재는 정확히 하나"를 보장하는
 * 것은 `currentNavHref`의 몫이다 — 부모/자식이 같은 메뉴에 있으면 둘 다 참이
 * 되는 것이 이 함수 층위에서는 정상이다.
 */
export function isRouteCurrent(href: string, pathname: string | null): boolean {
  if (pathname === null) return false;
  if (href === "/") return pathname === "/";
  return pathname === href || pathname.startsWith(`${href}/`);
}

/** 항목이 자기 href와 alias 중 하나로라도 현재에 걸리는가. */
function itemMatches(item: NavItem, pathname: string | null): boolean {
  if (item.match === "exact") {
    if (pathname === item.href) return true;
  } else if (isRouteCurrent(item.href, pathname)) {
    return true;
  }
  return (item.aliases ?? []).some((alias) => isRouteCurrent(alias, pathname));
}

/**
 * RSP-23(docs/improvement.md): 메뉴 집합에서 현재인 항목의 href를 **하나만**
 * 고른다. 여러 항목이 걸리면 가장 구체적인 것(일치한 경로가 가장 긴 것)이 이긴다.
 *
 * 단순 boolean을 각 링크가 독립적으로 재계산하면 `/recommend/history`에서
 * `번호 추천`과 `추천 이력`이 동시에 `aria-current="page"`가 된다. 더 나쁜 것은
 * TabBar였다 — 인디케이터는 `findIndex`로 첫 매치 하나만 쓰는데 링크는 각자
 * 판정해서, 시각 표시와 보조 기술이 서로 다른 답을 냈다. 두 컴포넌트가 이 함수
 * 하나를 공유하면 그 비대칭이 구조적으로 불가능해진다.
 */
export function currentNavHref(
  items: readonly NavItem[],
  pathname: string | null,
): string | undefined {
  let best: NavItem | undefined;
  let bestLength = -1;

  for (const item of items) {
    if (!itemMatches(item, pathname)) continue;
    // 구체성은 "실제로 일치한 경로"의 길이로 잰다 — alias로 걸린 항목은 alias
    // 길이가 기준이어야 `/analysis`(9자)가 `/data`(5자)보다 구체적으로 잡힌다.
    const matchedLength = Math.max(
      isRouteCurrent(item.href, pathname) ? item.href.length : -1,
      ...(item.aliases ?? []).map((alias) => (isRouteCurrent(alias, pathname) ? alias.length : -1)),
    );
    if (matchedLength > bestLength) {
      best = item;
      bestLength = matchedLength;
    }
  }

  return best?.href;
}

/**
 * 4대 축 IA(kraft-redesign-plan.md §4): Generate·Analyze·Insights·Community.
 * "보관함"은 어느 축에도 속하지 않는 개인 유틸리티라 여기서 빠지고 헤더의
 * 계정 영역 옆(utility nav)으로 옮겼다 — `site-header.tsx` 참고. URL은
 * 그대로다(R-13) — 이 배열은 라벨·묶음만 바꾼다.
 *
 * "인사이트" 그룹은 `primary-nav.tsx`에서 개별 링크가 아니라 `DropdownMenu`
 * 하나로 묶여 렌더된다 — 9개였던 최상위 항목을 4개로 줄이는 것이 이 재구성의
 * 핵심이므로, 그룹 자체를 접지 않으면 항목 수만 재배치될 뿐 목표를 달성하지
 * 못한다.
 */
export const PRIMARY_NAV: NavGroup[] = [
  {
    title: "번호 뽑기",
    items: [
      { href: ROUTES.recommend, label: "번호 추천" },
      // I-21: /recommend/history가 어느 내비게이션에도 없는 고아 라우트였다 —
      // noindex라 검색으로도 못 왔다. 여기 추가해 최소 한 곳에서는 도달 가능하게 한다.
      //
      // RSP-23: 부모(`/recommend`)가 같은 메뉴에 있으므로 exact다. subtree로 두면
      // 이 항목 아래에 하위 경로가 생겼을 때 다시 부모와 겹친다.
      { href: ROUTES.recommendHistory, label: "추천 이력", match: "exact" },
    ],
  },
  {
    title: "진단",
    items: [{ href: ROUTES.analysis, label: "내 조합 진단" }],
  },
  {
    title: "인사이트",
    items: [
      { href: ROUTES.data, label: "데이터 개요" },
      { href: ROUTES.frequency, label: "번호별 출현" },
      { href: ROUTES.stats, label: "당첨 패턴" },
      { href: ROUTES.companion, label: "함께 나온 번호" },
      { href: ROUTES.info("data-source"), label: "데이터 출처" },
      { href: ROUTES.info("methodology"), label: "방법론" },
    ],
  },
  {
    title: "커뮤니티",
    items: [{ href: ROUTES.community, label: "커뮤니티" }],
  },
];

/** `primary-nav.tsx`가 "인사이트" 그룹을 드롭다운으로 접기 위해 찾아 쓰는 제목. */
export const INSIGHTS_GROUP_TITLE = "인사이트";

/**
 * 모바일 하단 탭. 5개를 넘기면 터치 타깃이 44px 아래로 내려간다(§12.7).
 *
 * RSP-08(docs/improvement.md): 이 배열의 **길이**가 CSS에 두 번 하드코딩돼 있다 —
 * `shell.module.css:180`의 `calc(100% / 5)`와 `:191-210`의 `data-active-index`
 * 규칙 5개. 항목을 늘리거나 줄이면 그 두 곳을 반드시 함께 고쳐야 한다. 안 고치면
 * 조용히 틀린다: 4개면 인디케이터가 활성 탭보다 좁게 그려지고, 6개면 여섯 번째에서
 * 매칭되는 규칙이 없어 아예 사라진다. `.tabBar` 자체는 `grid-auto-columns: 1fr`
 * 이라 개수에 무관하다 — 어긋나는 것은 `.indicator`뿐이다.
 *
 * RSP-24(docs/improvement.md): `데이터` 탭이 통계 상세 4개를 alias로 흡수한다.
 * 이 화면들은 `/data` 하위가 아니라 별도 최상위 URL이라, alias가 없으면
 * 1152px 미만에서 정보 구조가 통째로 끊긴다(다섯 탭 모두 비활성).
 */
export const TAB_BAR_ITEMS: NavItem[] = [
  { href: ROUTES.home, label: "홈" },
  { href: ROUTES.recommend, label: "추천" },
  {
    href: ROUTES.data,
    label: "데이터",
    aliases: [ROUTES.frequency, ROUTES.stats, ROUTES.companion, ROUTES.analysis],
  },
  { href: ROUTES.community, label: "커뮤니티" },
  { href: ROUTES.saved, label: "보관함" },
];

export const FOOTER_NAV: NavGroup[] = [
  {
    title: "서비스 안내",
    items: [
      { href: ROUTES.info("about"), label: "운영자 소개" },
      { href: ROUTES.info("data-source"), label: "데이터 출처" },
      { href: ROUTES.info("methodology"), label: "분석 방법론" },
      { href: ROUTES.status, label: "서비스 상태" },
    ],
  },
  {
    title: "이용 안내",
    items: [
      { href: ROUTES.info("faq"), label: "자주 묻는 질문" },
      { href: ROUTES.info("community-guidelines"), label: "커뮤니티 이용규칙" },
      { href: ROUTES.info("responsible-play"), label: "건전한 이용" },
      { href: ROUTES.info("contact"), label: "문의하기" },
    ],
  },
  {
    title: "정책",
    items: [
      { href: ROUTES.info("terms"), label: "이용약관" },
      { href: ROUTES.info("privacy"), label: "개인정보처리방침" },
    ],
  },
];
