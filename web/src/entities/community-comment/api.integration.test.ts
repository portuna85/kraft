import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it } from "vitest";

import { server } from "../../../tests/msw/server";

import { createComment, deleteComment, fetchCommentPage, getCommentPage } from "./api";

/**
 * MSW를 실제로 거치는 통합 테스트
 *
 * 댓글은 2단 중첩 스키마(§25.6 — 답글은 페이징 집계에서 빠진다)라, 손으로 만든 목이
 * 아니라 실제 fetch → MSW → Valibot 경로에서 중첩 구조가 계속 파싱되는지 확인한다.
 */
describe("댓글 API — MSW 통합", () => {
  beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
  afterEach(() => server.resetHandlers());
  afterAll(() => server.close());

  beforeEach(() => {
    document.cookie = "XSRF-TOKEN=csrf-xyz; path=/";
  });

  it("서버(SSR) 조회가 상위 댓글과 중첩 답글을 함께 통과시킨다", async () => {
    const page = await getCommentPage(1);
    expect(page.topLevel).toHaveLength(1);
    expect(page.topLevel[0]?.replies).toHaveLength(1);
    expect(page.totalTopLevelComments).toBe(1);
  });

  it("브라우저 페이지 조회도 같은 스키마를 통과한다", async () => {
    const page = await fetchCommentPage(1, 0);
    expect(page.topLevel[0]?.replies[0]?.content).toBe("답글");
  });

  it("작성한 댓글이 응답 스키마를 통과한다", async () => {
    const created = await createComment(1, "새 댓글", null);
    expect(created.content).toBe("새 댓글");
    expect(created.parentId).toBeNull();
  });

  it("삭제는 204를 받고 null을 돌려준다", async () => {
    await expect(deleteComment(1)).resolves.toBeNull();
  });
});
