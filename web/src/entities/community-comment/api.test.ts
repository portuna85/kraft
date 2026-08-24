import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { initOf, jsonResponse, mockFetch, urlOf } from "@/shared/api/test/fetch-mock";

import { createComment, deleteComment, fetchCommentPage, getCommentPage } from "./api";

beforeEach(() => {
  document.cookie = "XSRF-TOKEN=csrf-xyz; path=/";
});

afterEach(() => {
  vi.unstubAllGlobals();
});

const pageBody = { topLevel: [], totalTopLevelComments: 0, page: 0, size: 50, totalPages: 0 };

const commentBody = {
  id: 1,
  postId: 7,
  parentId: null,
  ownerId: 10,
  authorNickname: "닉네임",
  content: "댓글",
  deleted: false,
  createdAt: "2025-01-01T00:00:00Z",
  targetPage: 0,
  replies: [],
};

// 서버(SSR 첫 페이지)와 브라우저(다음 페이지)는 서로 다른 base(직접 백엔드 vs 상대
// 경로 프록시)를 쓰도록 설계돼 있어 전체 URL 문자열은 다르다. 대신 두 경로가 같은
// path+query 접미사를 만드는지가 실제 불변식이다 — 페이지네이션이 SSR 첫 페이지와
// 어긋나면 안 되기 때문.
describe("getCommentPage(서버) vs fetchCommentPage(브라우저) — 같은 접미사", () => {
  it("getCommentPage는 no-store로 백엔드 내부 URL을 직접 호출한다", async () => {
    const spy = mockFetch(jsonResponse(pageBody));

    await getCommentPage(7, 0);

    expect(urlOf(spy)).toBe(
      "http://backend:8080/api/v1/community/posts/7/comments?page=0&size=50",
    );
    const init = initOf(spy) as { cache?: string };
    expect(init.cache).toBe("no-store");
  });

  it("fetchCommentPage는 같은 path+query를 상대 경로로 호출한다", async () => {
    const spy = mockFetch(jsonResponse(pageBody));

    await fetchCommentPage(7, 0);

    expect(urlOf(spy)).toBe("/api/v1/community/posts/7/comments?page=0&size=50");
  });

  it("페이지 번호가 바뀌어도 두 함수가 같은 접미사 규칙을 따른다", async () => {
    const serverSpy = mockFetch(jsonResponse(pageBody));
    await getCommentPage(7, 2);
    const serverSuffix = urlOf(serverSpy).replace("http://backend:8080", "");

    vi.unstubAllGlobals();

    const browserSpy = mockFetch(jsonResponse(pageBody));
    await fetchCommentPage(7, 2);

    expect(urlOf(browserSpy)).toBe(serverSuffix);
  });
});

describe("createComment", () => {
  it("content·parentId를 본문에 실어 POST한다", async () => {
    const spy = mockFetch(jsonResponse(commentBody));

    await createComment(7, "내용", null);

    expect(urlOf(spy)).toBe("/api/v1/community/posts/7/comments");
    const init = initOf(spy) as { method?: string; body?: string };
    expect(init.method).toBe("POST");
    expect(JSON.parse(init.body ?? "{}")).toEqual({ content: "내용", parentId: null });
  });
});

describe("deleteComment", () => {
  it("댓글 id 경로로 DELETE한다", async () => {
    const spy = mockFetch(new Response(null, { status: 204 }));

    await deleteComment(3);

    expect(urlOf(spy)).toBe("/api/v1/community/comments/3");
    expect((initOf(spy) as { method?: string }).method).toBe("DELETE");
  });
});
