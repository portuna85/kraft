import { BackendError } from "@/lib/api";
import { backendBaseUrl } from "@/lib/backend-url";
import type { components } from "@/lib/generated/api-types";
// KF-07: RecommendationItemView는 추천 생성 응답과 커뮤니티 첨부 뷰가 공유하는 스키마다 —
// 이 파일이 따로 파생시키던 버전은 explanationCodes를 좁히지 않아 features 쪽보다
// 부정확했다. lib/domain의 공유 정의로 통일한다.
import type { RecommendationItem } from "@/lib/domain/recommendation";

// 커뮤니티 목록/상세는 짧은 주기로 갱신되는 공개 콘텐츠라 ISR을 쓰되, 사용자 정보는
// 이 응답에 절대 섞이지 않는다 — 로그인 상태·소유권은 클라이언트가 /session과 대조한다.
const REVALIDATE_COMMUNITY_LIST = 30;

export type PostCategory = NonNullable<components["schemas"]["CommunityPostResponse"]["category"]>;
export type PostStatus = NonNullable<components["schemas"]["CommunityPostResponse"]["status"]>;
export type PostSort = "latest" | "weekly_popular";

export type RecommendationAttachment = Omit<
  components["schemas"]["RecommendationAttachmentView"],
  "items"
> & {
  items: RecommendationItem[];
};

export type CommunityPost = Omit<
  components["schemas"]["CommunityPostResponse"],
  "recommendationAttachment"
> & {
  recommendationAttachment: RecommendationAttachment | null;
};

export type CommunityComment = components["schemas"]["CommunityCommentResponse"];

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

async function fetchCommunityJson<T>(path: string, init: RequestInit): Promise<T> {
  const signal = AbortSignal.timeout(5000);
  const response = await fetch(`${backendBaseUrl}${path}`, { ...init, signal });
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
  return fetchCommunityJson<PageResponse<CommunityPost>>(
    `/api/v1/community/posts?${params.toString()}`,
    { next: { revalidate: REVALIDATE_COMMUNITY_LIST, tags: ["community:posts"] } }
  );
}

// FE-064 (미해결, 백엔드 설계 필요): 이 GET은 백엔드에서 조회수를 증가시키는 부수효과를
// 갖는다(CommunityPostService — "세션/중복 방지는 이번 범위에서 과설계라 생략, 단순 조회수").
// 그런데 여기서 ISR로 캐시하므로 캐시가 적중하는 동안에는 백엔드에 요청이 닿지 않아
// 조회수가 집계되지 않고, 화면에 보이는 조회수·좋아요 수도 최대 revalidate 주기만큼 오래됐다.
// 프런트 단독으로 no-store로 바꾸면 조회수는 정확해지지만 상세 페이지의 캐시 이점을 전부 잃는다.
// 올바른 해법은 조회수 write를 캐시하지 않는 별도 엔드포인트/이벤트로 분리하는 것이며,
// 그 전까지 이 트레이드오프를 의도된 현재 상태로 남긴다.
export async function getCommunityPost(id: number): Promise<CommunityPost> {
  return fetchCommunityJson<CommunityPost>(`/api/v1/community/posts/${id}`, {
    next: { revalidate: REVALIDATE_COMMUNITY_LIST, tags: ["community:posts", `community:post:${id}`] },
  });
}

// edit 폼 초기화 전용 — ISR 캐시를 완전히 우회해 자기-409 루프(캐시된 옛 version으로
// 폼을 채운 뒤 본인이 연속 수정하면 "다른 곳에서 먼저 수정" 오탐)를 근본적으로 막는다.
export async function getCommunityPostFresh(id: number): Promise<CommunityPost> {
  return fetchCommunityJson<CommunityPost>(`/api/v1/community/posts/${id}`, {
    cache: "no-store",
  });
}

export async function getCommunityComments(
  postId: number,
  page = 0,
  size = DEFAULT_COMMENT_PAGE_SIZE
): Promise<CommunityCommentPage> {
  return fetchCommunityJson<CommunityCommentPage>(
    `/api/v1/community/posts/${postId}/comments?page=${page}&size=${size}`,
    { next: { revalidate: REVALIDATE_COMMUNITY_LIST, tags: [`community:post:${postId}`] } }
  );
}
