import { browserFetch } from "@/lib/browser-api";
import { getDeviceToken } from "@/lib/device-token";
import {
  DEFAULT_COMMENT_PAGE_SIZE,
  type CommunityComment,
  type CommunityCommentPage,
  type CommunityPost,
  type PageResponse,
} from "@/lib/community-api";

export type CommunitySession = {
  loggedIn: boolean;
  userId: number | null;
  nickname: string | null;
  activeProviders: ("google" | "naver")[];
};

// CookieCsrfTokenRepository(double-submit, §4.3 ADR-0002)가 발급하는 쿠키를 읽어
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
