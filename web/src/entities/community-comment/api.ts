import { browserMutate, browserQuery, noContentSchema, serverFetch } from "@/shared/api/transport";
import { serverEnv } from "@/shared/config/env";

import {
  commentPageSchema,
  communityCommentSchema,
  type CommentPage,
  type CommunityComment,
} from "./schema";

/** 백엔드 CommunityCommentService.DEFAULT_PAGE_SIZE와 맞춘다. */
export const COMMENT_PAGE_SIZE = 50;

/**
 * 댓글 첫 페이지를 **서버에서** 가져온다 — improvement_fe.md §23.10 신규 요구
 *
 * 레거시는 댓글을 전부 CSR로 가져와서, 초기 HTML에 "댓글 0개"가 찍힌 뒤 나중에 채워졌다.
 * 색인에도 UX에도 손해다. 첫 페이지는 SSR하고 그 다음 페이지·작성·삭제만 브라우저가 맡는다.
 *
 * 캐시하지 않는다. 댓글은 자주 바뀌고, 게시글 상세 자체가 조회수 부수효과 때문에
 * no-store라 여기만 캐시해도 얻을 게 없다.
 */
export function getCommentPage(postId: number, page = 0): Promise<CommentPage> {
  return serverFetch(
    `${serverEnv.backendInternalUrl}/api/v1/community/posts/${postId}/comments?page=${page}&size=${COMMENT_PAGE_SIZE}`,
    commentPageSchema,
    { cache: { mode: "no-store" } },
  );
}

/** 브라우저에서 다음 페이지를 이어 받는다. */
export function fetchCommentPage(postId: number, page: number): Promise<CommentPage> {
  return browserQuery(
    `/api/v1/community/posts/${postId}/comments?page=${page}&size=${COMMENT_PAGE_SIZE}`,
    commentPageSchema,
  );
}

export function createComment(
  postId: number,
  content: string,
  parentId: number | null,
): Promise<CommunityComment> {
  return browserMutate(`/api/v1/community/posts/${postId}/comments`, communityCommentSchema, {
    method: "POST",
    body: { content, parentId },
  });
}

export function deleteComment(commentId: number): Promise<null> {
  return browserMutate(`/api/v1/community/comments/${commentId}`, noContentSchema, {
    method: "DELETE",
  });
}
