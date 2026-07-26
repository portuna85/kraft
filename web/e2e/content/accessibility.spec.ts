import { test, expect } from "@playwright/test";
import { expectNoA11yViolations } from "../lib/expect-no-a11y-violations";
import { gotoAndWaitForRealContent } from "../lib/goto-real-content";

// F-01: §6-2 픽스처 백엔드로 실콘텐츠를 렌더한 상태에서 axe를 돌린다. 폴백/에러
// 상태는 기본 트랙(e2e/accessibility.spec.ts)이 맡는다.
const REAL_CONTENT_PAGES: Array<{ name: string; path: string }> = [
  { name: "홈", path: "/" },
  { name: "번호 빈도", path: "/frequency" },
  { name: "통계", path: "/stats" },
  { name: "동반 출현", path: "/companion" },
  { name: "커뮤니티 목록", path: "/community" },
];

for (const { name, path } of REAL_CONTENT_PAGES) {
  test(`${name}(실콘텐츠) — 라이트 모드 접근성 위반 없음`, async ({ page }) => {
    await gotoAndWaitForRealContent(page, path);
    await expectNoA11yViolations(page);
  });
}

test("홈(실콘텐츠) — 다크 모드 접근성 위반 없음", async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem("kraft-theme", "dark");
  });
  await gotoAndWaitForRealContent(page, "/");
  await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
  await expectNoA11yViolations(page);
});

test("커뮤니티 게시글 상세(익명, 실콘텐츠) — 접근성 위반 없음", async ({ page }) => {
  await page.route("**/api/v1/community/posts/1/comments*", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ topLevel: [], totalTopLevelComments: 0, page: 0, totalPages: 0 }),
    })
  );

  await gotoAndWaitForRealContent(page, "/community/posts/1");
  await expectNoA11yViolations(page);
});

test("커뮤니티 게시글 상세(로그인 상태, 실콘텐츠) — 접근성 위반 없음", async ({ page }) => {
  await page.route("**/api/v1/community/session", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        loggedIn: true,
        userId: 1,
        nickname: "테스트유저",
        activeProviders: ["google"],
      }),
    })
  );
  await page.route("**/api/v1/community/posts/1/comments*", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ topLevel: [], totalTopLevelComments: 0, page: 0, totalPages: 0 }),
    })
  );

  await gotoAndWaitForRealContent(page, "/community/posts/1");
  await expect(page.getByLabel("댓글 작성")).toBeVisible();
  await expectNoA11yViolations(page);
});
