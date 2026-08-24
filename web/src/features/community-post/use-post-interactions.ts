import { getMyInteractions } from "@/entities/community-post/interactions";
import { useResource } from "@/shared/hooks/use-resource";

/**
 * FE-DATA-01(docs/improvement.md): 게시글 상세 한 화면에서 `ReactionBar`·
 * `BlockedPostGate`·`BlockButton`이 각자 개인 상호작용을 따로 물었다 — 차단 목록만
 * 2번 중복(`GET /blocked-users`), 좋아요/북마크까지 합치면 로그인 초기 진입에 개인
 * API 호출이 최대 3회였다. `getMyInteractions([postId])` 응답에 이미
 * `blockedUserIds`(전체 목록, postId 스코프 아님)가 함께 들어 있으므로, 셋 모두 같은
 * `me:interactions:${postId}` 리소스 키로 `useResource`를 불러 dedupe한다 — 마운트
 * 순서와 무관하게 실제 네트워크 요청은 1회만 나간다.
 */
export function usePostInteractions(postId: number, loggedIn: boolean) {
  const state = useResource(loggedIn ? `me:interactions:${postId}` : null, () =>
    getMyInteractions([postId]),
  );
  return state.status === "success" ? state.data : null;
}
