import { BackendError, type RequiredApi } from "@/lib/api";
import { backendBaseUrl } from "@/lib/backend-url";
import type { components } from "@/lib/generated/api-types";

// 커뮤니티 목록/상세는 짧은 주기로 갱신되는 공개 콘텐츠라 ISR을 쓰되, 사용자 정보는
// 이 응답에 절대 섞이지 않는다 — 로그인 상태·소유권은 클라이언트가 /session과 대조한다.
const REVALIDATE_COMMUNITY_LIST = 30;

export type PostCategory = NonNullable<components["schemas"]["CommunityPostResponse"]["category"]>;
export type PostStatus = NonNullable<components["schemas"]["CommunityPostResponse"]["status"]>;
export type PostSort = "latest" | "weekly_popular";

type RecommendationItem = Omit<RequiredApi<components["schemas"]["RecommendationItemView"]>, "score"> & {
    score: number | null;
};

export type RecommendationAttachment = Omit<
  RequiredApi<components["schemas"]["RecommendationAttachmentView"]>,
  "items"
> & {
  items: RecommendationItem[];
};

export type CommunityPost = Omit<
  RequiredApi<components["schemas"]["CommunityPostResponse"]>,
  "recommendationAttachment"
> & {
  recommendationAttachment: RecommendationAttachment | null;
};

export type CommunityComment = Omit<
  RequiredApi<components["schemas"]["CommunityCommentResponse"]>,
  "parentId" | "ownerId" | "targetPage" | "replies"
> & {
  parentId: number | null;
  ownerId: number | null;
  targetPage: number | null;
  replies: CommunityComment[];
};

export type PageResponse<T> = {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

// 댓글 목록 응답은 상위 댓글 페이지 + 각 상위 댓글에 중첩된 답글 형태다(PageResponse<T>와
// 달리 items가 아닌 topLevel, totalElements가 아닌 totalTopLevelComments — 답글은 페이징
// 집계에서 제외된다). 백엔드 CommunityCommentPageResponse와 1:1 대응.
export type CommunityCommentPage = {
  topLevel: CommunityComment[];
  totalTopLevelComments: number;
  page: number;
  size: number;
  totalPages: number;
};

export const DEFAULT_COMMENT_PAGE_SIZE = 50;

async function fetchCommunityJson<T>(path: string): Promise<T> {
  const signal = AbortSignal.timeout(5000);
  const response = await fetch(`${backendBaseUrl}${path}`, {
    signal,
    next: { revalidate: REVALIDATE_COMMUNITY_LIST },
  });
  if (!response.ok) {
    let code = "BACKEND_ERROR";
    let message = `Backend request failed: ${path} (${response.status})`;
    try {
      const body = (await response.clone().json()) as { code?: string; message?: string };
      if (body.code) code = body.code;
      if (body.message) message = body.message;
    } catch {
      // 바디 파싱 실패 시 기본 메시지 유지
    }
    throw new BackendError(code, message, response.status);
  }
  return response.json() as Promise<T>;
}

export async function getCommunityPosts(
  page = 0,
  size = 20,
  options: { category?: PostCategory; sort?: PostSort; query?: string } = {}
): Promise<PageResponse<CommunityPost>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (options.category) params.set("category", options.category);
  if (options.sort) params.set("sort", options.sort);
  if (options.query) params.set("query", options.query);
  return fetchCommunityJson<PageResponse<CommunityPost>>(`/api/v1/community/posts?${params.toString()}`);
}

export async function getCommunityPost(id: number): Promise<CommunityPost> {
  return fetchCommunityJson<CommunityPost>(`/api/v1/community/posts/${id}`);
}

export async function getCommunityComments(
  postId: number,
  page = 0,
  size = DEFAULT_COMMENT_PAGE_SIZE
): Promise<CommunityCommentPage> {
  return fetchCommunityJson<CommunityCommentPage>(
    `/api/v1/community/posts/${postId}/comments?page=${page}&size=${size}`
  );
}
