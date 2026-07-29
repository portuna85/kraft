// Phase 1 2단계(docs/improvement_gpt.md §3.2): DesktopNav/MobileBottomNav/MobileSecondaryMenu가
// 공유하는 단일 진실 공급원. 기존 nav-links.tsx의 primaryLinks/statsLinks/isCurrent를
// 그대로 옮기고 라벨만 목표 IA에 맞춰 바꿨다(저장 번호→보관함, 통계→데이터) — 라우트는
// 그대로라 기능 변화는 없다.

export interface NavLink {
  href: string;
  label: string;
}

export const primaryLinks: readonly NavLink[] = [
  { href: "/", label: "홈" },
  { href: "/recommend", label: "번호 추천" },
  { href: "/community", label: "커뮤니티" },
  { href: "/saved", label: "보관함" },
];

export const dataLinks: readonly NavLink[] = [
  { href: "/frequency", label: "출현 통계" },
  { href: "/stats", label: "패턴 통계" },
  { href: "/companion", label: "동반 출현" },
  { href: "/analysis", label: "번호 분석" },
];

export function isCurrent(href: string, pathname: string): boolean {
  if (href === "/") return pathname === "/";
  return pathname === href || pathname.startsWith(`${href}/`);
}
