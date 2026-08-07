import { dataLinks, primaryLinks, type NavLink } from "./nav-items";

// 홈의 "서비스 바로가기" 격자.
//
// nav-items.ts가 아니라 별도 모듈에 두는 이유: nav-items.ts는 DesktopNav·MobileBottomNav
// 같은 클라이언트 컴포넌트가 import하므로 브라우저 번들에 실린다. serviceLinks는 서버
// 컴포넌트(home-sections.tsx)만 쓰는데, 같은 파일에 두면 트리셰이킹되지 않고 전 라우트의
// 공용 청크를 키운다 — 실제로 그 0.1KB 때문에 여유가 없던 /ops가 번들 예산을 넘겼다.
//
// 라우트를 다시 하드코딩하면 nav-items.ts가 단일 진실 공급원이라는 전제가 깨지므로(FE-007)
// 기존 배열들을 조합해서만 만든다 — dataLinks에 페이지를 추가하면 홈에도 자동으로 나타난다.
// 제외 대상: 홈 자신(`/`), 내부 운영 화면 `/ops`(robots noindex),
// 서비스가 아니라 정책·안내 문서이고 푸터가 이미 전부 링크하는 `/info/*`.
export const serviceLinks: readonly NavLink[] = [
  ...primaryLinks.filter((link) => link.href !== "/"),
  { href: "/recommend/history", label: "추천 이력" },
  ...dataLinks,
];
