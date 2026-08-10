import { HttpResponse, http } from "msw";
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it } from "vitest";

import { ApiError } from "@/shared/api/error";

import { server } from "../../../tests/msw/server";

import { createPost, fetchLatestPost, updatePost } from "./composer";

/**
 * MSW를 실제로 거치는 통합 테스트 — improvement_fe.md §21.7
 *
 * `features/community-composer/post-form.test.tsx`는 composer 함수를 목으로 대체해
 * 409 이후 폼 동작만 검증한다. 여기서는 실제 fetch → MSW → Valibot 경로에서 409가
 * ApiError.isConflict로 식별되는지, 그리고 정상 응답이 게시글 스키마를 통과하는지
 * 확인한다 — 두 테스트가 서로 다른 층을 덮는다.
 */
describe("게시글 작성·수정 API — MSW 통합", () => {
  beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
  afterEach(() => server.resetHandlers());
  afterAll(() => server.close());

  beforeEach(() => {
    document.cookie = "XSRF-TOKEN=csrf-xyz; path=/";
  });

  it("작성 응답이 게시글 스키마를 통과한다", async () => {
    const post = await createPost({ title: "제목", content: "내용", category: "GENERAL" }, null);
    expect(post.title).toBe("제목");
    expect(post.status).toBe("PUBLISHED");
  });

  it("낙관적 잠금 충돌(409)을 ApiError.isConflict로 식별한다", async () => {
    server.use(
      http.put("/api/v1/community/posts/:id", () =>
        HttpResponse.json({ code: "VERSION_CONFLICT" }, { status: 409 }),
      ),
    );

    const error = await updatePost(
      1,
      { title: "제목", content: "내용", category: "GENERAL" },
      3,
    ).then(
      () => null,
      (cause: unknown) => cause as ApiError,
    );

    expect(error?.isConflict).toBe(true);
  });

  it("충돌 후 최신 게시글을 다시 읽으면 스키마를 통과한다", async () => {
    const latest = await fetchLatestPost(1);
    expect(latest.id).toBe(1);
  });
});
