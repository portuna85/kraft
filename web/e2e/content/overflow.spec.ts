import { test, expect } from "@playwright/test";
import { expectNoOverflow } from "../lib/expect-no-overflow";
import { gotoAndWaitForRealContent } from "../lib/goto-real-content";

// playwright.content.config.ts로만 실행된다(픽스처 백엔드가 떠 있는 상태).
// 기본 설정(playwright.config.ts)의 responsive.spec.ts는 백엔드가 없다는 전제라
// 이 라우트들에서 항상 error.tsx만 검사해왔다 — 여기서는 실제 렌더된 콘텐츠를
// 먼저 확인한 뒤 오버플로를 검사해, 픽스처가 깨져 조용히 에러 화면을 검사하게
// 되는 상황을 재발 방지한다.
// 639/1023 = 640/1024 브레이크포인트 바로 아래(경계 −1px) — 회귀를 가장 싸게 잡는 폭.
const WIDTHS = [320, 639, 640, 768, 1023, 1024, 1280, 1440] as const;

for (const width of WIDTHS) {
  test.describe(`${width}px`, () => {
    test.use({ viewport: { width, height: 900 } });

    test("/ — 홈 히어로 실렌더 후 오버플로 없음", async ({ page }) => {
      await gotoAndWaitForRealContent(page, "/");
      await expect(page.locator(".result-panel .balls")).toBeVisible();
      await expect(page.getByRole("heading", { name: "1189회" })).toBeVisible();
      await expectNoOverflow(page);
    });

    if (width === 320) {
      test("/ — 모바일 당첨금의 세전·세후 금액을 스크롤 없이 읽을 수 있다", async ({ page }) => {
        await gotoAndWaitForRealContent(page, "/");
        const table = page.getByRole("region", { name: "당첨금 표" });
        await expect(table.getByText("세전 당첨금").first()).toBeVisible();
        await expect(table.getByText("세후 예상 금액").first()).toBeVisible();
        await expect(table.locator(".prize-table-after-tax-value").first()).toBeVisible();
      });
    }

    test("/frequency — 출현 통계 실렌더 후 오버플로 없음", async ({ page }) => {
      await gotoAndWaitForRealContent(page, "/frequency");
      await expect(page.locator(".freq-summary")).toBeVisible();
      await expect(page.getByTestId("frequency-grid").getByTestId("frequency-item").first()).toBeVisible();
      await expectNoOverflow(page);
    });

    test("/frequency — 필터 전환 후 오버플로 없음", async ({ page }) => {
      // KF-06: 필터 클릭은 브라우저에서 /api/v1/stats/frequency?limit=100을 직접 호출한다
      // (frequency-filter-client.tsx). 이 트랙은 Caddy 없이 standalone Next만 떠 있어
      // 그 경로를 프록시할 게 없다 — 프록시 계약 자체는 playwright.proxy.config.ts가
      // 전담하므로, 여기서는 /ops 오버플로 테스트(위)와 동일하게 응답을 목킹해 이 테스트
      // 본연의 목적(필터 전환 후 CSS 오버플로 여부)에 집중한다.
      await page.route("**/api/v1/stats/frequency**", (route) =>
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            totalRounds: 100,
            frequencies: Array.from({ length: 45 }, (_, i) => ({
              ballNumber: i + 1,
              frequency: 10 + ((i * 3) % 15),
              lastRound: 1189 - (i % 10),
            })),
            topSix: { balls: [], wonFirstPrize: false, firstPrizeHistory: [] },
            bottomSix: { balls: [], wonFirstPrize: false, firstPrizeHistory: [] },
          }),
        })
      );

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

    // /status는 기존 e2e(status.spec.ts)가 "백엔드 없음 → 폴백 문구" 상태만
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

      // RW-P1-07(WebKit 확장)에서 드러남: DOM 값을 한 번에 바꾸는 fill은 고부하
      // 병렬 실행에서 하이드레이션과 경합해 React 상태가 빈 값으로 남을 수 있다.
      // 실제 사용자가 입력할 때와 같은 키보드 input 이벤트를 발생시키고, controlled
      // state가 반영돼 버튼이 활성화됐는지 확인한 뒤 클릭한다.
      await page.goto("/ops", { waitUntil: "networkidle" });
      const tokenInput = page.getByPlaceholder("X-Ops-Token 값을 입력하세요");
      await tokenInput.pressSequentially("secret-token");
      await expect(tokenInput).toHaveValue("secret-token");
      const summaryButton = page.getByRole("button", { name: "운영 상태 확인" });
      await expect(summaryButton).toBeEnabled();
      await summaryButton.click();
      await expect(page.getByText("1230회")).toBeVisible();
      await expectNoOverflow(page);
    });
  });
}
