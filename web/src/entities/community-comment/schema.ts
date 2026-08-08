import * as v from "valibot";

/**
 * 댓글 계약 — improvement_fe.md §24.2(1)
 *
 * 2단 구조다. 상위 댓글만 페이징되고 **답글은 페이징 집계에서 빠진다**(§25.6) —
 * totalTopLevelComments가 상위 댓글 수인 이유다. 답글까지 세면 "3개"라고 적어 놓고
 * 화면에는 7개가 보이는 상태가 된다.
 */
export const communityCommentSchema: v.GenericSchema<CommunityComment> = v.object({
  id: v.number(),
  postId: v.number(),
  parentId: v.nullable(v.number()),
  /** tombstone된 댓글은 소유권 판정 UI를 숨기려고 백엔드가 ownerId까지 가린다. */
  ownerId: v.nullable(v.number()),
  authorNickname: v.string(),
  content: v.string(),
  deleted: v.boolean(),
  createdAt: v.string(),
  /** 작성 직후 응답에만 채워진다 — 방금 쓴 댓글이 몇 페이지에 있는지 알려 준다. */
  targetPage: v.nullable(v.number()),
  get replies() {
    return v.array(communityCommentSchema);
  },
});

export type CommunityComment = {
  id: number;
  postId: number;
  parentId: number | null;
  ownerId: number | null;
  authorNickname: string;
  content: string;
  deleted: boolean;
  createdAt: string;
  targetPage: number | null;
  replies: CommunityComment[];
};

export const commentPageSchema = v.object({
  topLevel: v.array(communityCommentSchema),
  totalTopLevelComments: v.number(),
  page: v.number(),
  size: v.number(),
  totalPages: v.number(),
});

export type CommentPage = v.InferOutput<typeof commentPageSchema>;

/** 백엔드 @Size(max = 1000)과 같아야 한다 — 넘겨 보내면 400이다. */
export const COMMENT_MAX_LENGTH = 1000;

export function isCommentSubmittable(content: string): boolean {
  const trimmed = content.trim();
  return trimmed.length > 0 && trimmed.length <= COMMENT_MAX_LENGTH;
}
