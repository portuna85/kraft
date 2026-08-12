import { expect, test } from "@playwright/test";

/**
 * H-03: 게시글 상세가 첨부된 추천 세트를 렌더하는지 확인한다.
 *
 * 작성(첨부 선택) 흐름은 로그인 세션이 필요해 컴포넌트 테스트가 이미 검증했다
 * (`post-form.test.tsx`, `recommendation-attachment-picker.test.tsx`) — 이 트랙은
 * community-and-status.spec.ts와 같은 이유로 읽기 경로에 집중한다.
 */
test.describe("게시글 추천 첨부 렌더", () => {
  test("첨부가 있는 글은 상세에서 추천 세트를 보여준다", async ({ page }) => {
    await page.goto("/community/posts/2");

    const attachment = page.getByRole("region", { name: "첨부된 추천 세트" });
    await expect(attachment).toBeVisible();
    await expect(attachment.getByText("균형 잡힌 조합")).toBeVisible();
    await expect(attachment.getByText(/1150회까지 반영/)).toBeVisible();
  });

  test("첨부가 없는 글은 첨부 영역을 렌더하지 않는다", async ({ page }) => {
    await page.goto("/community/posts/1");

    await expect(page.getByRole("region", { name: "첨부된 추천 세트" })).not.toBeVisible();
  });
});
