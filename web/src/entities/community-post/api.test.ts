import { afterEach, describe, expect, it, vi } from "vitest";

import { headersOf, initOf, jsonResponse, mockFetch, urlOf } from "@/shared/api/test/fetch-mock";

import { DEFAULT_PAGE_SIZE, getPost, getPostPage, REVALIDATE_COMMUNITY_LIST_SECONDS } from "./api";
import type { ListParams } from "./query";

afterEach(() => {
  vi.unstubAllGlobals();
});

const listParams: ListParams = {
  page: 0,
  category: undefined,
  sort: "latest",
  query: undefined,
};

const postBody = {
  id: 1,
  ownerId: 10,
  authorNickname: "닉네임",
  title: "제목",
  content: "본문",
  category: "GENERAL",
  status: "PUBLISHED",
  version: 1,
  createdAt: "2025-01-01T00:00:00Z",
  updatedAt: "2025-01-01T00:00:00Z",
  likeCount: 0,
  commentCount: 0,
  viewCount: 0,
  recommendationAttachment: null,
};

const pageBody = {
  items: [postBody],
  page: 0,
  size: DEFAULT_PAGE_SIZE,
  totalElements: 1,
  totalPages: 1,
};

describe("getPostPage", () => {
  it("page·size·sort를 쿼리스트링으로 붙인다(category·query 생략)", async () => {
    const spy = mockFetch(jsonResponse(pageBody));

    await getPostPage(listParams);

    expect(urlOf(spy)).toBe(
      "http://backend:8080/api/v1/community/posts?page=0&size=20&sort=latest",
    );
  });

  it("category·query가 있으면 함께 붙인다", async () => {
    const spy = mockFetch(jsonResponse(pageBody));

    await getPostPage({ ...listParams, category: "WIN_STORY", query: "당첨" });

    const url = urlOf(spy);
    expect(url).toContain("category=WIN_STORY");
    expect(url).toContain("query=%EB%8B%B9%EC%B2%A8");
  });

  it("community:posts 태그로 30초 재검증한다", async () => {
    const spy = mockFetch(jsonResponse(pageBody));

    await getPostPage(listParams);

    const init = initOf(spy) as { next?: { revalidate: number; tags: string[] } };
    expect(init.next).toEqual({
      revalidate: REVALIDATE_COMMUNITY_LIST_SECONDS,
      tags: ["community:posts"],
    });
  });
});

describe("getPost", () => {
  it("no-store로 호출해 캐시되지 않는다(조회수 부수효과 보존)", async () => {
    const spy = mockFetch(jsonResponse(postBody));

    await getPost(1);

    const init = initOf(spy) as { cache?: string; next?: unknown };
    expect(init.cache).toBe("no-store");
    expect(init.next).toBeUndefined();
    expect(urlOf(spy)).toBe("http://backend:8080/api/v1/community/posts/1");
  });

  it("스키마와 어긋난 응답은 통과시키지 않는다", async () => {
    mockFetch(jsonResponse({ ...postBody, likeCount: "many" }));

    await expect(getPost(2)).rejects.toMatchObject({ kind: "server", code: "SCHEMA_MISMATCH" });
  });

  it("헤더에 content-type 없이 accept만 보낸다", async () => {
    const spy = mockFetch(jsonResponse(postBody));

    await getPost(3);

    expect(headersOf(spy)["accept"]).toBe("application/json");
    expect(headersOf(spy)["content-type"]).toBeUndefined();
  });
});
