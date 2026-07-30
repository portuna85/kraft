import { type RequiredApi } from "@/lib/api";
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

// KF-07: 생성 스키마(CommunitySessionResponse)가 이미 있는데 이 타입을 완전 수동으로
// 다시 정의하고 있었다. springdoc은 nullable 여부와 무관하게 전 필드를 optional(`?`)로만
// 표기하므로(required/nullable 구분 메타데이터 없음), RequiredApi로 "항상 응답에 포함됨"을
// 복원하고, 실제로 null일 수 있는 userId/nickname과 리터럴 유니온으로 좁혀야 하는
// activeProviders만 개별적으로 덮어쓴다(community-api.ts의 기존 Omit 패턴과 동일).
export type CommunitySession = Omit<
  RequiredApi<components["schemas"]["CommunitySessionResponse"]>,
  "userId" | "nickname" | "activeProviders"
> & {
  userId: number | null;
  nickname: string | null;
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

export async function getCommunitySession(): Promise<CommunitySession> {
  return browserFetch<CommunitySession>("/api/v1/community/session", {
    cache: "no-store",
  });
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

// KF-07: 세 필드 모두 백엔드에서 primitive int라 항상 존재한다 — RequiredApi만으로 정확하다.
export type IdentityMergeResult = RequiredApi<components["schemas"]["IdentityMergeResult"]>;

// KF-07: label만 백엔드에서 정당하게 nullable(String)이라 개별적으로 덮어쓴다.
export type MySavedNumber = Omit<
  RequiredApi<components["schemas"]["SavedNumberResponse"]>,
  "label"
> & {
  label: string | null;
};

// 로그인 계정 귀속 — 같은 브라우저의 익명 기기 토큰 기록을 계정으로 옮긴다.
// X-Device-Token은 CSRF 토큰과 별개로 여전히 필요하다(어떤 기기의 기록을 옮길지 지정).
export async function claimDevice(): Promise<IdentityMergeResult> {
  return browserFetch<IdentityMergeResult>("/api/v1/community/session/claim-device", {
    method: "POST",
    headers: writeHeaders({ "X-Device-Token": getDeviceToken() }),
  });
}

export async function getMySavedNumbers(): Promise<MySavedNumber[]> {
  return browserFetch<MySavedNumber[]>("/api/v1/community/me/saved-numbers", { cache: "no-store" });
}

export async function getMyRecommendationSets(): Promise<RecommendationSetSummary[]> {
  return browserFetch<RecommendationSetSummary[]>("/api/v1/community/me/recommendation-sets", {
    cache: "no-store",
  });
}
