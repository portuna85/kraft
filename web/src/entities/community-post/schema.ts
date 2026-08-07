import * as v from "valibot";

/**
 * 게시글 계약 — improvement_fe.md §3.6, §24.2(1)
 * 열거값은 백엔드 계약이라 변경 금지다.
 */
export const POST_CATEGORIES = [
  "RECOMMENDATION_SHARE",
  "ROUND_ANALYSIS",
  "WIN_STORY",
  "QUESTION",
  "GENERAL",
] as const;

export type PostCategory = (typeof POST_CATEGORIES)[number];

export const CATEGORY_LABELS: Record<PostCategory, string> = {
  RECOMMENDATION_SHARE: "조합 공유",
  ROUND_ANALYSIS: "회차 분석",
  WIN_STORY: "당첨 후기",
  QUESTION: "질문",
  GENERAL: "자유",
};

export const POST_SORTS = ["latest", "weekly_popular"] as const;
export type PostSort = (typeof POST_SORTS)[number];

export const SORT_LABELS: Record<PostSort, string> = {
  latest: "최신순",
  weekly_popular: "주간 인기",
};

export const POST_STATUSES = [
  "PUBLISHED",
  "HIDDEN_BY_AUTHOR",
  "HIDDEN_BY_MODERATOR",
  "DELETED",
] as const;

export const communityPostSchema = v.object({
  id: v.number(),
  ownerId: v.number(),
  authorNickname: v.string(),
  title: v.string(),
  content: v.string(),
  category: v.picklist(POST_CATEGORIES),
  status: v.picklist(POST_STATUSES),
  version: v.number(),
  createdAt: v.string(),
  updatedAt: v.string(),
  likeCount: v.number(),
  commentCount: v.number(),
  viewCount: v.number(),
  recommendationAttachment: v.nullable(v.unknown()),
});

export type CommunityPost = v.InferOutput<typeof communityPostSchema>;

export const communityPostPageSchema = v.object({
  items: v.array(communityPostSchema),
  page: v.number(),
  size: v.number(),
  totalElements: v.number(),
  totalPages: v.number(),
});

export type CommunityPostPage = v.InferOutput<typeof communityPostPageSchema>;

/** 삭제·차단된 글은 목록에서 사라지지 않고 문맥을 보존한 채 남는다(§25.6 tombstone). */
export function isTombstone(post: CommunityPost): boolean {
  return post.status !== "PUBLISHED";
}
