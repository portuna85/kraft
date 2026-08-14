import * as v from "valibot";

import { browserMutate, browserQuery, noContentSchema } from "@/shared/api/transport";

/**
 * 로그인 사용자의 개인 상호작용
 *
 * 공개 ISR HTML에는 사용자 상태가 들어가지 않는다(§5.4 보안 모델). 그래서 좋아요·북마크·
 * 차단 여부는 브라우저가 따로 물어본다. 전부 no-store다 — 공유 캐시에 담기면 다른
 * 사용자에게 내 상태가 보인다.
 */

const interactionsSchema = v.object({
  likedPostIds: v.array(v.number()),
  bookmarkedPostIds: v.array(v.number()),
  blockedUserIds: v.array(v.number()),
});

export type CommunityInteractions = v.InferOutput<typeof interactionsSchema>;

/** 백엔드 @Size(max = 100). 넘겨 보내면 400이다(KB-10). */
export const MAX_INTERACTION_POST_IDS = 100;

/**
 * 내 상호작용 조회 — **postIds가 비면 호출하지 않는다**(레거시 C-1).
 *
 * 빈 배열을 보내면 쿼리 파라미터 자체가 사라져 `@RequestParam`이 없는 요청이 되고
 * 백엔드가 400을 낸다. 호출부가 매번 기억하게 두지 않고 여기서 막는다.
 */
export function getMyInteractions(postIds: readonly number[]): Promise<CommunityInteractions> {
  if (postIds.length === 0) {
    return Promise.resolve({ likedPostIds: [], bookmarkedPostIds: [], blockedUserIds: [] });
  }

  const ids = postIds.slice(0, MAX_INTERACTION_POST_IDS).join(",");
  return browserQuery(`/api/v1/community/me/interactions?postIds=${ids}`, interactionsSchema);
}

/**
 * 차단 목록 — postIds 없이 부를 수 있는 유일한 경로다(C-1이 이것 때문에 생겼다).
 * 게시글이 하나도 없는 화면에서도 차단 상태를 알아야 한다.
 */
export function getBlockedUsers(): Promise<number[]> {
  return browserQuery("/api/v1/community/me/blocked-users", v.array(v.number()));
}

export function likePost(postId: number, liked: boolean): Promise<null> {
  return browserMutate(`/api/v1/community/posts/${postId}/like`, noContentSchema, {
    method: liked ? "PUT" : "DELETE",
  });
}

export function bookmarkPost(postId: number, bookmarked: boolean): Promise<null> {
  return browserMutate(`/api/v1/community/posts/${postId}/bookmark`, noContentSchema, {
    method: bookmarked ? "PUT" : "DELETE",
  });
}

export function blockUser(userId: number, blocked: boolean): Promise<null> {
  return browserMutate(`/api/v1/community/users/${userId}/block`, noContentSchema, {
    method: blocked ? "PUT" : "DELETE",
  });
}

/**
 * 게시글 삭제. `expectedVersion`이 **필수**다 — 낙관적 잠금(§25.6).
 *
 * 내가 보고 있던 글을 다른 탭에서 수정한 뒤 여기서 지우면, 버전이 없을 때는 무엇을
 * 지우는지 모르는 채로 지운다. 버전이 어긋나면 백엔드가 409로 막는다.
 */
export function deletePost(postId: number, expectedVersion: number): Promise<null> {
  return browserMutate(
    `/api/v1/community/posts/${postId}?expectedVersion=${expectedVersion}`,
    noContentSchema,
    { method: "DELETE" },
  );
}
