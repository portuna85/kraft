import { browserFetch } from "@/lib/browser-api";
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
    headers: writeHeaders(),
    includeDeviceToken: recommendationSetId !== null,
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
  return browserFetch<CommunityInteractions>(
    `/api/v1/community/me/interactions?${params.toString()}`,
    { cache: "no-store" },
    isCommunityInteractions
  );
}

export async function reportContent(
  targetType: "POST" | "COMMENT" | "USER",
  targetId: number,
  reason: components["schemas"]["CreateReportRequest"]["reason"]
): Promise<void> {
  await browserFetch<void>("/api/v1/community/reports", {
    method: "POST",
    headers: writeHeaders(),
    body: JSON.stringify({ targetType, targetId, reason }),
  });
}

// C-1: /me/interactions는 postIds가 필수라 페이지 로드 시 한 번만 차단 목록을 가져오는
// 용도로 재사용할 수 없다(빈 배열을 보내면 파라미터 자체가 사라져 400이 난다).
export async function getMyBlockedUserIds(): Promise<number[]> {
  return browserFetch<number[]>(
    "/api/v1/community/me/blocked-users",
    { cache: "no-store" },
    isNumberArray
  );
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

function isRecommendationSetSummary(body: unknown): body is RecommendationSetSummary {
  if (typeof body !== "object" || body === null) return false;
  const b = body as Record<string, unknown>;
  return typeof b.id === "number" && typeof b.strategy === "string" && Array.isArray(b.items);
}

function isRecommendationSetSummaryPage(body: unknown): body is PageResponse<RecommendationSetSummary> {
  if (typeof body !== "object" || body === null) return false;
  const b = body as Record<string, unknown>;
  return Array.isArray(b.items) && b.items.every(isRecommendationSetSummary);
}

function isCommunityInteractions(body: unknown): body is CommunityInteractions {
  if (typeof body !== "object" || body === null) return false;
  const b = body as Record<string, unknown>;
  return (
    Array.isArray(b.likedPostIds) &&
    Array.isArray(b.bookmarkedPostIds) &&
    Array.isArray(b.blockedUserIds)
  );
}

function isNumberArray(body: unknown): body is number[] {
  return Array.isArray(body) && body.every((item) => typeof item === "number");
}

function isMySavedNumber(body: unknown): body is MySavedNumber {
  if (typeof body !== "object" || body === null) return false;
  const b = body as Record<string, unknown>;
  return typeof b.id === "number" && Array.isArray(b.numbers);
}

function isSaveMySavedNumberResult(body: unknown): body is SaveMySavedNumberResult {
  if (typeof body !== "object" || body === null) return false;
  const b = body as Record<string, unknown>;
  return typeof b.created === "boolean" && isMySavedNumber(b.savedNumber);
}

function isMySavedNumberMatchResultArray(body: unknown): body is MySavedNumberMatchResult[] {
  return (
    Array.isArray(body) &&
    body.every((item) => {
      if (typeof item !== "object" || item === null) return false;
      const i = item as Record<string, unknown>;
      return (
        isMySavedNumber(i.savedNumber) &&
        typeof i.round === "number" &&
        typeof i.matchedCount === "number" &&
        typeof i.prizeTier === "string"
      );
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
      headers: writeHeaders(),
      includeDeviceToken: true,
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

// C-2: page/size에 기본값을 둬 페이지 인자를 받을 수 있게 확장한다 — 계정 라이브러리에
// "더 보기"를 추가하려면(C-2-4) 고정 size=50으로는 부족하다. 인자 없이 호출하는 기존
// 코드는 이전과 동일하게 첫 페이지(size=50)를 받는다.
export async function getMyRecommendationSets(
  page = 0,
  size = 50
): Promise<PageResponse<RecommendationSetSummary>> {
  return browserFetch<PageResponse<RecommendationSetSummary>>(
    `/api/v1/community/me/recommendation-sets?page=${page}&size=${size}`,
    { cache: "no-store" },
    isRecommendationSetSummaryPage
  );
}

export type MySavedNumberMatchResult = {
  savedNumber: MySavedNumber;
  round: number;
  drawDate: string;
  drawNumbers: number[];
  bonusNumber: number;
  matchedCount: number;
  bonusMatch: boolean;
  prizeTier: string;
};

export type SaveMySavedNumberResult = {
  savedNumber: MySavedNumber;
  created: boolean;
};

// C-2: 로그인 세션으로 저장·회차 대조·삭제 — 아래 5개는 MyLibraryController가 이미
// 제공하지만 지금까지 프론트에서 한 번도 호출되지 않았다(GET 목록 2개만 쓰였다).
// /api/v1/community/** 체인이라 쓰기 요청엔 writeHeaders()의 CSRF 토큰이 필요하다.
export async function saveMySavedNumber(
  numbers: number[],
  label?: string,
  source?: string
): Promise<SaveMySavedNumberResult> {
  return browserFetch<SaveMySavedNumberResult>(
    "/api/v1/community/me/saved-numbers",
    {
      method: "POST",
      headers: writeHeaders(),
      body: JSON.stringify({ numbers, label, source }),
    },
    isSaveMySavedNumberResult
  );
}

export async function deleteMySavedNumber(id: number): Promise<void> {
  await browserFetch<void>(`/api/v1/community/me/saved-numbers/${id}`, {
    method: "DELETE",
    headers: writeHeaders(),
  });
}

export async function getMySavedNumberMatches(round: string): Promise<MySavedNumberMatchResult[]> {
  return browserFetch<MySavedNumberMatchResult[]>(
    `/api/v1/community/me/saved-numbers/matches?round=${encodeURIComponent(round)}`,
    { cache: "no-store" },
    isMySavedNumberMatchResultArray
  );
}

export async function getMyRecommendationSet(id: number): Promise<RecommendationSetSummary> {
  return browserFetch<RecommendationSetSummary>(
    `/api/v1/community/me/recommendation-sets/${id}`,
    { cache: "no-store" },
    isRecommendationSetSummary
  );
}

export async function deleteMyRecommendationSet(id: number): Promise<void> {
  await browserFetch<void>(`/api/v1/community/me/recommendation-sets/${id}`, {
    method: "DELETE",
    headers: writeHeaders(),
  });
}
