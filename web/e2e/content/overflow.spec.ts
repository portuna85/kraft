import { test, expect } from "@playwright/test";
import { expectNoOverflow } from "../lib/expect-no-overflow";
import { gotoAndWaitForRealContent } from "../lib/goto-real-content";

// §6-2: playwright.content.config.ts로만 실행된다(픽스처 백엔드가 떠 있는 상태).
// 기본 설정(playwright.config.ts)의 responsive.spec.ts는 백엔드가 없다는 전제라
// 이 라우트들에서 항상 error.tsx만 검사해왔다 — 여기서는 실제 렌더된 콘텐츠를
// 먼저 확인한 뒤 오버플로를 검사해, 픽스처가 깨져 조용히 에러 화면을 검사하게
// 되는 상황(§6-2가 원래 겪었던 문제)을 재발 방지한다.
// §6-3: 639/1023 = 640/1024 브레이크포인트 바로 아래(경계 −1px) — 회귀를 가장 싸게 잡는 폭.
const WIDTHS = [320, 639, 768, 1023, 1024, 1280] as const;

for (const width of WIDTHS) {
  test.describe(`${width}px`, () => {
    test.use({ viewport: { width, height: 900 } });

    test("/ — 홈 히어로 실렌더 후 오버플로 없음", async ({ page }) => {
      await gotoAndWaitForRealContent(page, "/");
      await expect(page.locator(".result-panel .balls")).toBeVisible();
      await expect(page.getByRole("heading", { name: "1189회" })).toBeVisible();
      await expectNoOverflow(page);
    });

    test("/frequency — 출현 통계 실렌더 후 오버플로 없음", async ({ page }) => {
      await gotoAndWaitForRealContent(page, "/frequency");
      await expect(page.locator(".freq-summary")).toBeVisible();
      await expect(page.getByTestId("frequency-grid").getByTestId("frequency-item").first()).toBeVisible();
      await expectNoOverflow(page);
    });

    test("/frequency — 필터 전환 후 오버플로 없음", async ({ page }) => {
      await gotoAndWaitForRealContent(page, "/frequency");
      await page.getByRole("button", { name: "최근 100회" }).click();
      await expect(page.getByRole("button", { name: "최근 100회" })).toHaveAttribute("aria-pressed", "true");
      await expect(page.getByTestId("frequency-grid").getByTestId("frequency-item").first()).toBeVisible();
      await expectNoOverflow(page);
    });

    test("/stats — 패턴 통계 실렌더 후 오버플로 없음", async ({ page }) => {
      await gotoAndWaitForRealContent(page, "/stats");
      await expect(page.getByTestId("pattern-list").first()).toBeVisible();
      await expectNoOverflow(page);
    });

    test("/companion — 동반 출현 실렌더 후 오버플로 없음", async ({ page }) => {
      await gotoAndWaitForRealContent(page, "/companion");
      await expect(page.getByTestId("companion-list")).toBeVisible();
      await expectNoOverflow(page);
    });

    // §6-3: /status는 기존 e2e(status.spec.ts)가 "백엔드 없음 → 폴백 문구" 상태만
    // 검증해왔다 — 여기서는 정상 데이터(freshness+incidents) 상태를 검증한다.
    test("/status — 정상 데이터 실렌더 후 오버플로 없음", async ({ page }) => {
      await gotoAndWaitForRealContent(page, "/status");
      await expect(page.getByTestId("status-incident-list")).toBeVisible();
      await expectNoOverflow(page);
    });

    // /ops는 픽스처 백엔드가 아니라 ops.spec.ts와 동일하게 page.route()로 /ops-api/*를
    // 목킹한다 — route-level loading.tsx가 없는 순수 클라이언트 셸이라
    // gotoAndWaitForRealContent(스켈레톤 소멸 대기)가 필요 없다.
    test("/ops — 토큰 없는 기본 상태 오버플로 없음", async ({ page }) => {
      await page.goto("/ops");
      await expect(page.getByRole("heading", { name: "회차 운영 대시보드" })).toBeVisible();
      await expectNoOverflow(page);
    });

    test("/ops — 운영 상태 조회 후 오버플로 없음", async ({ page }) => {
      await page.route("**/ops-api/summary", (route) =>
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            service: "kraft-lotto",
            timezone: "Asia/Seoul",
            status: "정상",
            latestRound: 1230,
            latestDrawDate: "2026-01-03",
            checkedAt: "2026-01-03T12:00:00Z",
            fresh: true,
          }),
        })
      );

      await page.goto("/ops");
      await page.getByPlaceholder("X-Ops-Token 값을 입력하세요").fill("secret-token");
      await page.getByRole("button", { name: "운영 상태 확인" }).click();
      await expect(page.getByText("1230회")).toBeVisible();
      await expectNoOverflow(page);
    });
  });
}
