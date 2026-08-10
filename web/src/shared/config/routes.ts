/**
 * 라우트 레지스트리 — improvement_fe.md §24.2 (5번: 공개 라우트 URL은 전환 전후로
 * 바뀌면 안 된다)
 *
 * URL을 문자열 리터럴로 흩뿌리지 않고 한 곳에 모은다. IA(내비 라벨·묶음)는 바꾸되
 * URL은 그대로 유지한다는 결정(R-13)을 지키려면 URL 집합이 한 파일에 보여야 한다.
 */

export const ROUTES = {
  home: "/",
  recommend: "/recommend",
  recommendHistory: "/recommend/history",
  data: "/data",
  frequency: "/frequency",
  stats: "/stats",
  companion: "/companion",
  analysis: "/analysis",
  saved: "/saved",
  status: "/status",
  community: "/community",
  communityWrite: "/community/write",
  communityPost: (id: number | string) => `/community/posts/${id}`,
  communityPostEdit: (id: number | string) => `/community/posts/${id}/edit`,
  info: (slug: InfoPageSlug) => `/info/${slug}`,
  ops: "/ops",
} as const;

/** 백엔드·사이트맵과 공유하는 계약 — 임의로 늘리거나 줄이지 않는다(§3.6). */
export const INFO_PAGE_SLUGS = [
  "data-source",
  "methodology",
  "faq",
  "privacy",
  "terms",
  "responsible-play",
  "community-guidelines",
  "contact",
  "about",
] as const;

export type InfoPageSlug = (typeof INFO_PAGE_SLUGS)[number];

/**
 * 세션 API를 호출해도 되는 경로 — 불변식 I-4(§14.5).
 * 이 밖의 경로에서 세션을 조회하면 완전 공개 페이지에 익명 트래픽이 발생한다.
 */
export const SESSION_SCOPED_PREFIXES = ["/community", "/saved", "/recommend"] as const;

export function isSessionScopedPath(pathname: string): boolean {
  return SESSION_SCOPED_PREFIXES.some(
    (prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`),
  );
}
