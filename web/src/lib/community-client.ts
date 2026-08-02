import { browserFetch } from "@/lib/browser-api";
import { getDeviceToken } from "@/lib/device-token";
// KF-07: RecommendationSetSummary는 lib/domain(중립 위치)에 있다 — 이전에는
// features/recommendation/types를 직접 참조해 lib→features 역방향 의존이었다.
import type { RecommendationSetSummary } from "@/lib/domain/recommendation";
import type { components } from "@/lib/generated/api-types";
import {
  DEFAULT_COMMENT_PAGE_SIZE,
  type CommunityComment,
  type CommunityCommentPage,
  type CommunityPost,
  type PageResponse,
} from "@/lib/community-api";

// OpenAPI가 필드 존재와 nullability를 정확히 표현하므로 공급자 리터럴만 UI 경계에서 좁힌다.
export type CommunitySession = Omit<
  components["schemas"]["CommunitySessionResponse"],
  "activeProviders"
> & {
  activeProviders: ("google" | "naver")[];
};

// CookieCsrfTokenRepository(double-submit)가 발급하는 쿠키를 읽어
// 상태 변경 요청에 X-XSRF-TOKEN 헤더로 그대로 되돌려 보낸다.
function readXsrfToken(): string | null {
  const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
  return match ? decodeURIComponent(match[1]) : null;
}

function writeHeaders(extra?: Record<string, string>): HeadersInit {
  const token = readXsrfToken();
  return {
    "Content-Type": "application/json",
    ...(token ? { "X-XSRF-TOKEN": token } : {}),
    ...extra,
  };
}

// KF-09: 이 4개 응답은 원시 `as T` 캐스트만 하고 있어, 백엔드 계약이 어긋나도
// 런타임에 곧장 오류가 나지 않고 필드가 undefined인 채 화면 깊숙이 전파됐다 —
// 새 검증 라이브러리는 번들 예산(bundle-budget.json) 제약으로 못 쓰므로, 최상위
// 형태만 확인하는 손으로 쓴 가드로 최소한의 방어선을 둔다. 형태가 어긋나면
// 기존 BrowserApiError 경로로 실패시켜 호출부가 이미 처리하는 오류 UI를 재사용한다.
function isCommunitySession(body: unknown): body is CommunitySession {
  if (typeof body !== "object" || body === null) return false;
  const b = body as Record<string, unknown>;
  return (
    typeof b.loggedIn === "boolean" &&
    (typeof b.userId === "number" || b.userId === null) &&
    (typeof b.nickname === "string" || b.nickname === null) &&
    Array.isArray(b.activeProviders)
  );
}

export async function getCommunitySession(): Promise<CommunitySession> {
  return browserFetch<CommunitySession>(
    "/api/v1/community/session",
    { cache: "no-store" },
    isCommunitySession
  );
}

export function loginUrl(provider: "google" | "naver"): string {
  return `/oauth2/authorization/${provider}`;
}

export async function logout(): Promise<boolean> {
  try {
    const response = await fetch("/logout", {
      method: "POST",
      headers: writeHeaders(),
      signal: AbortSignal.timeout(5000),
    });
    return response.ok;
  } catch {
    return false;
  }
}

// KB-04: 탈퇴 — 백엔드가 같은 요청 안에서 세션을 무효화하므로 별도로 /logout을 호출할
// 필요는 없다. 204만 성공으로 본다(백엔드가 항상 그렇게 응답).
export async function withdraw(): Promise<boolean> {
  try {
    const response = await fetch("/api/v1/community/me/withdrawal", {
      method: "POST",
      headers: writeHeaders(),
      signal: AbortSignal.timeout(5000),
    });
    return response.ok;
  } catch {
    return false;
  }
}

export async function createPost(
  title: string,
  content: string,
  category: string,
  recommendationSetId: number | null
): Promise<CommunityPost> {
  return browserFetch<CommunityPost>("/api/v1/community/posts", {
    method: "POST",
    headers: writeHeaders(recommendationSetId ? { "X-Device-Token": getDeviceToken() } : undefined),
    body: JSON.stringify({ title, content, category, recommendationSetId }),
  });
}

/**
 * FE-066: 수정 중 버전 충돌이 났을 때 최신 상태를 브라우저에서 다시 읽는다.
 * 서버 쪽 getCommunityPost는 ISR 캐시를 타므로 방금 다른 사람이 올린 버전을 못 볼 수
 * 있어, 여기서는 browserFetch(캐시 없음)로 직접 조회한다.
 */
export async function fetchCommunityPost(id: number): Promise<CommunityPost> {
  return browserFetch<CommunityPost>(`/api/v1/community/posts/${id}`);
}

export async function updatePost(
  id: number,
  title: string,
  content: string,
  expectedVersion: number
): Promise<CommunityPost> {
  return browserFetch<CommunityPost>(`/api/v1/community/posts/${id}`, {
    method: "PUT",
    headers: writeHeaders(),
    body: JSON.stringify({ title, content, expectedVersion }),
  });
}

export async function deletePost(id: number, expectedVersion: number): Promise<void> {
  await browserFetch<void>(`/api/v1/community/posts/${id}?expectedVersion=${expectedVersion}`, {
    method: "DELETE",
    headers: writeHeaders(),
  });
}

export async function fetchCommunityComments(
  postId: number,
  page = 0,
  size = DEFAULT_COMMENT_PAGE_SIZE
): Promise<CommunityCommentPage> {
  return browserFetch<CommunityCommentPage>(
    `/api/v1/community/posts/${postId}/comments?page=${page}&size=${size}`,
    { cache: "no-store" }
  );
}

export async function createComment(
  postId: number,
  content: string,
  parentId: number | null
): Promise<CommunityComment> {
  return browserFetch<CommunityComment>(`/api/v1/community/posts/${postId}/comments`, {
    method: "POST",
    headers: writeHeaders(),
    body: JSON.stringify({ content, parentId }),
  });
}

export async function deleteComment(id: number): Promise<void> {
  await browserFetch<void>(`/api/v1/community/comments/${id}`, {
    method: "DELETE",
    headers: writeHeaders(),
  });
}

export type CommunityInteractions = {
  likedPostIds: number[];
  bookmarkedPostIds: number[];
  blockedUserIds: number[];
};

export async function likePost(postId: number): Promise<void> {
  await browserFetch<void>(`/api/v1/community/posts/${postId}/like`, { method: "PUT", headers: writeHeaders() });
}

export async function unlikePost(postId: number): Promise<void> {
  await browserFetch<void>(`/api/v1/community/posts/${postId}/like`, { method: "DELETE", headers: writeHeaders() });
}

export async function bookmarkPost(postId: number): Promise<void> {
  await browserFetch<void>(`/api/v1/community/posts/${postId}/bookmark`, { method: "PUT", headers: writeHeaders() });
}

export async function unbookmarkPost(postId: number): Promise<void> {
  await browserFetch<void>(`/api/v1/community/posts/${postId}/bookmark`, {
    method: "DELETE",
    headers: writeHeaders(),
  });
}

export async function getMyInteractions(postIds: number[]): Promise<CommunityInteractions> {
  const params = new URLSearchParams();
  postIds.forEach((id) => params.append("postIds", String(id)));
  return browserFetch<CommunityInteractions>(`/api/v1/community/me/interactions?${params.toString()}`, {
    cache: "no-store",
  });
}

export async function reportContent(
  targetType: "POST" | "COMMENT" | "USER",
  targetId: number,
  reason: string
): Promise<void> {
  await browserFetch<void>("/api/v1/community/reports", {
    method: "POST",
    headers: writeHeaders(),
    body: JSON.stringify({ targetType, targetId, reason }),
  });
}

export async function blockUser(userId: number): Promise<void> {
  await browserFetch<void>(`/api/v1/community/users/${userId}/block`, { method: "PUT", headers: writeHeaders() });
}

export async function unblockUser(userId: number): Promise<void> {
  await browserFetch<void>(`/api/v1/community/users/${userId}/block`, {
    method: "DELETE",
    headers: writeHeaders(),
  });
}

export type IdentityMergeResult = components["schemas"]["IdentityMergeResult"];

export type MySavedNumber = components["schemas"]["SavedNumberResponse"];

function isIdentityMergeResult(body: unknown): body is IdentityMergeResult {
  if (typeof body !== "object" || body === null) return false;
  const b = body as Record<string, unknown>;
  return (
    typeof b.mergedSavedNumberCount === "number" &&
    typeof b.duplicateSavedNumberCount === "number" &&
    typeof b.mergedRecommendationSetCount === "number"
  );
}

function isMySavedNumberArray(body: unknown): body is MySavedNumber[] {
  return (
    Array.isArray(body) &&
    body.every((item) => {
      if (typeof item !== "object" || item === null) return false;
      const i = item as Record<string, unknown>;
      return typeof i.id === "number" && Array.isArray(i.numbers);
    })
  );
}

function isRecommendationSetSummaryPage(body: unknown): body is PageResponse<RecommendationSetSummary> {
  if (typeof body !== "object" || body === null) return false;
  const b = body as Record<string, unknown>;
  return (
    Array.isArray(b.items) &&
    b.items.every((item) => {
      if (typeof item !== "object" || item === null) return false;
      const i = item as Record<string, unknown>;
      return typeof i.id === "number" && typeof i.strategy === "string" && Array.isArray(i.items);
    })
  );
}

// 로그인 계정 귀속 — 같은 브라우저의 익명 기기 토큰 기록을 계정으로 옮긴다.
// X-Device-Token은 CSRF 토큰과 별개로 여전히 필요하다(어떤 기기의 기록을 옮길지 지정).
export async function claimDevice(): Promise<IdentityMergeResult> {
  return browserFetch<IdentityMergeResult>(
    "/api/v1/community/session/claim-device",
    {
      method: "POST",
      headers: writeHeaders({ "X-Device-Token": getDeviceToken() }),
    },
    isIdentityMergeResult
  );
}

export async function getMySavedNumbers(): Promise<MySavedNumber[]> {
  return browserFetch<MySavedNumber[]>(
    "/api/v1/community/me/saved-numbers",
    { cache: "no-store" },
    isMySavedNumberArray
  );
}

// KB-05: 백엔드가 무제한 배열 대신 페이지네이션(PageResponse)을 반환하도록 바뀌었다 —
// 이 화면들에는 아직 "더 보기" UI가 없으므로, 오늘의 실사용 흐름을 그대로 유지할 만큼
// 넉넉한 size로 한 번에 받아 온다(무한 증식만 방어, 페이지 UI 도입은 필요해지면 별도로).
export async function getMyRecommendationSets(): Promise<PageResponse<RecommendationSetSummary>> {
  return browserFetch<PageResponse<RecommendationSetSummary>>(
    "/api/v1/community/me/recommendation-sets?page=0&size=50",
    { cache: "no-store" },
    isRecommendationSetSummaryPage
  );
}
