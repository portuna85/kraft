import { test, expect } from "@playwright/test";
import { gotoAndWaitForRealContent } from "../lib/goto-real-content";

// 픽스처 backend.mjs가 이 검색어에만 0건을 응답한다.
const NO_RESULT_QUERY = "존재하지않는검색어";

test("목록 항목에 작성 일시를 보여준다", async ({ page }) => {
  await gotoAndWaitForRealContent(page, "/community");

  // FE-047: 홈 커뮤니티 섹션은 날짜를 보여주는데 목록에는 없어 최신성 판단이 불가능했다.
  const item = page.locator(".community-post-list-item").first();
  const time = item.locator("time");
  await expect(time).toHaveAttribute("datetime", "2026-07-01T00:00:00Z");
  await expect(time).not.toBeEmpty();
});

test("검색 결과가 0건이면 빈 게시판과 다른 문구와 해제 경로를 보여준다", async ({ page }) => {
  await page.goto(`/community?query=${encodeURIComponent(NO_RESULT_QUERY)}`);

  // FE-048: 예전에는 두 경우 모두 "등록된 게시글이 없습니다."였다.
  await expect(page.getByText(`“${NO_RESULT_QUERY}” 검색 결과가 없습니다.`)).toBeVisible();
  await expect(page.getByRole("link", { name: "필터 해제하고 전체 보기" })).toBeVisible();
  await expect(page.getByText("아직 등록된 게시글이 없습니다.", { exact: false })).toHaveCount(0);
});

test("필터 해제 링크를 누르면 전체 목록으로 돌아온다", async ({ page }) => {
  await page.goto(`/community?query=${encodeURIComponent(NO_RESULT_QUERY)}`);
  await page.getByRole("link", { name: "필터 해제하고 전체 보기" }).click();

  await expect(page).toHaveURL(/\/community$/);
  await expect(page.locator(".community-post-list-item").first()).toBeVisible();
});

test("검색 조건이 있으면 결과 수와 해제 링크를 함께 보여준다", async ({ page }) => {
  await gotoAndWaitForRealContent(page, "/community?query=테스트");

  await expect(page.getByRole("status")).toContainText("검색 결과");
  await expect(page.getByRole("link", { name: "필터 해제" })).toBeVisible();
});
