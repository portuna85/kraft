// DesktopNav/MobileBottomNav/MobileSecondaryMenu가
// 공유하는 단일 진실 공급원. 기존 nav-links.tsx의 primaryLinks/statsLinks/isCurrent를
// 그대로 옮기고 라벨만 목표 IA에 맞춰 바꿨다(저장 번호→보관함, 통계→데이터) — 라우트는
// 그대로라 기능 변화는 없다.

import type { InfoPageSlug } from "./info-page-metadata";

export interface NavLink {
  href: string;
  label: string;
  /** 하단 탭처럼 폭이 좁은 곳에서 쓰는 짧은 라벨. 없으면 label을 그대로 쓴다. */
  shortLabel?: string;
}

export const primaryLinks: readonly NavLink[] = [
  { href: "/", label: "홈" },
  { href: "/recommend", label: "번호 추천", shortLabel: "추천" },
  { href: "/community", label: "커뮤니티" },
  { href: "/saved", label: "보관함" },
];

export const dataLinks: readonly NavLink[] = [
  { href: "/frequency", label: "출현 통계" },
  { href: "/stats", label: "패턴 통계" },
  { href: "/companion", label: "동반 출현" },
  { href: "/analysis", label: "번호 분석" },
];

// FE-007: 푸터가 안내 페이지 목록을 따로 하드코딩하고 있어, 새 안내 페이지를 추가해도
// 푸터에서 조용히 빠질 수 있었다. Record<InfoPageSlug, string>으로 두면 슬러그를
// 추가하는 순간 여기가 타입 오류로 막힌다.
// 라벨이 INFO_PAGE_METADATA의 정식 title과 다른 것은 의도된 것이다 — 푸터는 폭이 좁아
// "분석 방법론" 대신 "분석 기준"처럼 줄여 쓴다.
const INFO_NAV_LABELS: Record<InfoPageSlug, string> = {
  "data-source": "데이터 출처",
  methodology: "분석 기준",
  faq: "FAQ",
  privacy: "개인정보처리방침",
  terms: "이용약관",
  "responsible-play": "건전한 이용",
  "community-guidelines": "커뮤니티 이용규칙",
  contact: "문의하기",
  about: "운영자 소개",
};

function infoLink(slug: InfoPageSlug): NavLink {
  return { href: `/info/${slug}`, label: INFO_NAV_LABELS[slug] };
}

/** 푸터의 정보/정책 링크. */
export const footerLinks: readonly NavLink[] = [
  infoLink("data-source"),
  infoLink("methodology"),
  infoLink("faq"),
  infoLink("privacy"),
  infoLink("terms"),
  infoLink("responsible-play"),
  infoLink("community-guidelines"),
  infoLink("contact"),
  infoLink("about"),
];

export function isCurrent(href: string, pathname: string): boolean {
  if (href === "/") return pathname === "/";
  return pathname === href || pathname.startsWith(`${href}/`);
}
