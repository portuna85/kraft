import { expect, test } from "@playwright/test";

/**
 * KF-07(docs/improvement.md) — `/frequency`가 비어 있는 600px 데스크톱 광고
 * 사이드바를 예약했었다.
 *
 * `AdSenseSidebar`(shared/ui/ad-unit.tsx)의 `.adSidebarSlot` 래퍼가 `isDesktop`
 * 여부만으로 600px를 예약해, `NEXT_PUBLIC_ADSENSE_CLIENT_ID` 등이 비어 있어
 * `AdSenseUnit`이 결국 아무것도 렌더하지 않는 설정(이 e2e 빌드의 기본 상태)에서도
 * 빈 블록이 남았다. §10 4단계에서 `adUnitRendersContent()`로 실제로 렌더될 것이
 * 확실할 때만 예약하도록 고쳤다.
 */
test.describe("/frequency 사이드바가 광고 비활성 설정에서 빈 공간을 남기지 않는다", () => {
  test("데스크톱(1280px)에서 사이드바 영역 높이가 0이다", async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 900 });
    await page.goto("/frequency", { waitUntil: "networkidle" });

    // AdSenseUnit이 아무것도 렌더하지 않는 설정에서는 .adSidebarSlot 클래스
    // 자체가 안 붙는다(willRender=false) — CSS Modules가 클래스명을 해시하므로
    // 부분 일치로 찾는다. 못 찾으면 래퍼가 클래스 없이 렌더된 것이므로 통과.
    const sidebarHeight = await page
      .locator('[class*="adSidebarSlot"]')
      .evaluateAll((elements) =>
        elements.reduce((max, el) => Math.max(max, el.getBoundingClientRect().height), 0),
      );

    expect(sidebarHeight).toBe(0);
  });

  test("모바일(390px)에서도 사이드바 영역이 없다", async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto("/frequency", { waitUntil: "networkidle" });

    const sidebarUnit = page.getByLabel("사이드바 광고");
    await expect(sidebarUnit).toHaveCount(0);
  });
});
