import { expect, test } from "@playwright/test";

import { assertNotOccludedByFixedUi } from "../lib/responsive-assertions";

/**
 * 고정 UI(헤더·하단 탭바)가 콘텐츠 끝을 가리지 않아야 한다 — Phase 1
 * (docs/improvement_claude_fe.md F-05, docs/improvement_codex_fe.md FE-RSP-03).
 */
test.describe("고정 UI가 콘텐츠를 가리지 않는다", () => {
  test.use({ viewport: { width: 390, height: 844 } });

  test("모바일에서 마지막 footer 링크가 하단 탭바에 가려지지 않는다", async ({ page }) => {
    await page.goto("/", { waitUntil: "networkidle" });
    await page.locator("footer").scrollIntoViewIfNeeded();
    await assertNotOccludedByFixedUi(page, "footer a", ['nav[aria-label="바로가기"]']);
  });

  test("데스크톱(≥1024px)에서는 탭바 여백이 남지 않는다", async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 900 });
    await page.goto("/", { waitUntil: "networkidle" });
    const paddingBottom = await page.evaluate(() => {
      const main = document.querySelector("main");
      return main ? window.getComputedStyle(main).paddingBottom : null;
    });
    expect(paddingBottom, `main padding-bottom=${paddingBottom}`).not.toBeNull();
    expect(parseFloat(paddingBottom ?? "0")).toBeLessThan(56);
  });
});

test.describe("가로모드 광고 인셋 회귀 고정", () => {
  test.use({ viewport: { width: 844, height: 390 } });

  test("844×390에서 --fixed-bottom-inset이 0px이다", async ({ page }) => {
    await page.goto("/", { waitUntil: "networkidle" });
    const inset = await page.evaluate(() =>
      document.documentElement.style.getPropertyValue("--fixed-bottom-inset"),
    );
    expect(inset === "" || inset === "0px").toBe(true);
  });
});
