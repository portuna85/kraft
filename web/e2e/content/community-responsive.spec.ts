import { test, expect } from "@playwright/test";
import { expectNoOverflow } from "../lib/expect-no-overflow";
import { gotoAndWaitForRealContent } from "../lib/goto-real-content";

// /community* 라우트는 뷰포트 스냅숏 하나만 있어도 잡혔을 반응형 결함이 방치되기
// 쉬운 영역이었다 — 이 스펙은 픽스처 백엔드(playwright.content.config.ts)로 실콘텐츠를
// 렌더한 뒤, 익명 상태와 로그인 상태(page.route로 세션 목킹) 양쪽에서 오버플로·터치
// 타깃을 검사한다.
const WIDTHS = [320, 360, 390, 768] as const;

for (const width of WIDTHS) {
  test.describe(`${width}px`, () => {
    test.use({ viewport: { width, height: 900 } });

    test("/community — 목록 오버플로 없음", async ({ page }) => {
      await gotoAndWaitForRealContent(page, "/community");
      await expect(page.getByText("테스트 게시글")).toBeVisible();
      await expectNoOverflow(page);
    });

    test("/community/posts/1 — 상세(익명) 오버플로 없음", async ({ page }) => {
      await page.route("**/api/v1/community/posts/1/comments*", (route) =>
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            topLevel: [],
            totalTopLevelComments: 0,
            page: 0,
            totalPages: 0,
          }),
        })
      );
      await gotoAndWaitForRealContent(page, "/community/posts/1");
      await expect(page.getByRole("heading", { name: "테스트 게시글" })).toBeVisible();
      await expectNoOverflow(page);
    });

    test("/community/posts/2 — 극단 입력(200자 제목·URL 덩어리) 오버플로 없음", async ({ page }) => {
      await page.route("**/api/v1/community/posts/2/comments*", (route) =>
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            topLevel: [],
            totalTopLevelComments: 0,
            page: 0,
            totalPages: 0,
          }),
        })
      );
      await gotoAndWaitForRealContent(page, "/community/posts/2");
      await expectNoOverflow(page);
    });
  });
}

// ─────────────────────────────────────────────────────────────────────────────
// 로그인 상태: 헤더 로그아웃 버튼·소유자 액션·댓글 폼까지 렌더시켜 R-30·R-31·R-32가
// 실제로 고쳐졌는지 하나의 매트릭스로 검증한다.
// ─────────────────────────────────────────────────────────────────────────────
test.describe("로그인 상태", () => {
  test.beforeEach(async ({ page }) => {
    await page.route("**/api/v1/community/session", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          loggedIn: true,
          userId: 42,
          nickname: "테스트유저",
          activeProviders: ["google"],
        }),
      })
    );
    await page.route("**/api/v1/community/posts/1/comments*", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          topLevel: [
            {
              id: 1,
              postId: 1,
              ownerId: 42,
              authorNickname: "테스트유저",
              content: "첫 댓글",
              deleted: false,
              createdAt: "2026-07-01T00:00:00Z",
              replies: [],
            },
          ],
          totalTopLevelComments: 1,
          page: 0,
          totalPages: 1,
        }),
      })
    );
  });

  for (const width of [320, 360, 390] as const) {
    test(`${width}px — 헤더·소유자 액션·댓글 폼 오버플로 없음`, async ({ page }) => {
      await page.setViewportSize({ width, height: 900 });
      await gotoAndWaitForRealContent(page, "/community/posts/1");

      // Phase 1 2단계: 이 폭(<1024px)에서는 로그아웃 버튼(AccountMenu)이 보조 메뉴
      // 드로어 안으로 이동했다 — 열어야 보인다.
      await page.getByRole("button", { name: "메뉴 열기" }).click();
      const dialog = page.getByRole("dialog");
      await expect(dialog.getByRole("button", { name: "로그아웃" })).toBeVisible();
      await expect(page.getByRole("button", { name: "삭제" }).first()).toBeVisible();
      await expectNoOverflow(page);
    });
  }

  test("320px — 로그아웃 버튼 터치 타깃 ≥40px", async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 900 });
    await gotoAndWaitForRealContent(page, "/community/posts/1");

    await page.getByRole("button", { name: "메뉴 열기" }).click();
    const box = await page.getByRole("dialog").getByRole("button", { name: "로그아웃" }).boundingBox();
    expect(box).not.toBeNull();
    expect(box!.height).toBeGreaterThanOrEqual(40);
  });
});
