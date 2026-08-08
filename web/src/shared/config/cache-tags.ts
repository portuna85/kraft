/**
 * 캐시 태그 이름 — improvement_fe.md §24.2(3), R-6
 *
 * 백엔드 RevalidateWebhookListener.tagsFor()가 내보내는 문자열과 **정확히** 같아야 한다.
 * 어긋나면 커밋 후에도 화면이 옛 데이터를 계속 보여주는데, 아무 오류도 나지 않아서
 * 조용히 실패한다.
 *
 * shared에 두는 이유: 두 개 이상의 entity가 같은 태그를 단다(회차 갱신은 최신 회차와
 * 공개 이력을 함께 무너뜨린다). 계층 규칙상 entity끼리는 서로를 참조할 수 없으므로,
 * 여기 두지 않으면 각자 문자열을 다시 쓰게 되고 그게 정확히 R-6이 경고한 상황이다.
 * `/api/revalidate` Route Handler도 이 상수를 쓴다.
 */
export const CACHE_TAGS = {
  roundsLatest: "rounds:latest",
  statsAll: "stats:all",
  communityPosts: "community:posts",
} as const;

export type CacheTag = (typeof CACHE_TAGS)[keyof typeof CACHE_TAGS];
