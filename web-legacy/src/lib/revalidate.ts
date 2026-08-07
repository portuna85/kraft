export const REVALIDATE_LATEST = 60;       // 최신 당첨번호: 1분
export const REVALIDATE_STATS = 1800;      // 통계/동반/빈도: 30분
export const REVALIDATE_SITEMAP = 3600;    // 사이트맵: 1시간

// 캐시 태그 — revalidateTag() 대상. 백엔드 RevalidateWebhookListener.tagsFor()와
// 이름이 일치해야 한다(언어가 달라 완전한 공유는 못하지만, 태그 개수가 고정(2종)이라
// 경로 화이트리스트보다 드리프트 리스크가 훨씬 낮다).
export const TAG_ROUNDS_LATEST = "rounds:latest";
export const TAG_STATS = "stats:all";
