import { expect, test } from "@playwright/test";

/**
 * 커뮤니티 읽기 흐름 + 서비스 상태 + 안내 라우트 — T-9, T-10
 *
 * 익명 사용자는 커뮤니티를 읽을 수만 있다(작성·댓글·좋아요는 로그인 필요) — 그래서
 * 이 트랙은 읽기 경로에 집중한다. 쓰기 흐름(작성·댓글·신고)은 컴포넌트 테스트가
 * 로그인 세션을 목으로 만들어 이미 검증했다(Step A/B, `post-form.test.tsx`·
 * `comment-section.test.tsx`).
 */
test.describe("커뮤니티·상태·안내 읽기", () => {
  test("커뮤니티 목록에서 글 상세로 이동한다", async ({ page }) => {
    await page.goto("/community/posts/1");
    await expect(page.getByRole("heading", { name: "첫 글입니다" })).toBeVisible();
    await expect(page.getByText("본문 첫 줄")).toBeVisible();
  });

  test("게시글 상세가 상위 댓글과 답글을 함께 보여준다", async ({ page }) => {
    await page.goto("/community/posts/1");
    await expect(page.getByText("첫 댓글")).toBeVisible();
    await expect(page.getByText("답글입니다")).toBeVisible();
  });

  test("삭제된 댓글은 tombstone 문구로 자리를 지킨다", async ({ page }) => {
    await page.goto("/community/posts/1");
    await expect(page.getByText("삭제된 댓글입니다.")).toBeVisible();
  });

  test("비로그인은 댓글 작성 폼 대신 안내를 본다", async ({ page }) => {
    await page.goto("/community/posts/1");
    await expect(page.getByText("로그인하면 댓글을 남길 수 있습니다.")).toBeVisible();
  });

  test("글이 1페이지뿐이면 페이지네이션 대신 총 건수만 보인다 (improvement_fe_codex.md §12.8)", async ({
    page,
  }) => {
    await page.goto("/community");
    await expect(page.getByRole("heading", { name: "첫 글입니다" })).toBeVisible();
    await expect(page.getByText("총 1건")).toBeVisible();
    await expect(page.getByRole("navigation", { name: "페이지 이동" })).not.toBeVisible();
  });

  test("/status가 데이터 신선도와 수집·보정 이력을 렌더한다", async ({ page }) => {
    await page.goto("/status");
    await expect(page.getByText("정상 반영")).toBeVisible();
    await expect(page.getByText("당첨번호 자동 수집")).toBeVisible();
  });

  test("/info/faq가 렌더된다", async ({ page }) => {
    await page.goto("/info/faq");
    await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
  });
});
