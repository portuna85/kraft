import { expect, test } from "@playwright/test";

/**
 * KF-06(docs/improvement.md) — 하단 고정 UI에 층 계약이 없다 (부분: 토스트의
 * safe-area 누락).
 *
 * `.tabBar`(shell.module.css)와 `.adStickyMobile`(ad-unit.module.css)는 모두
 * `padding-bottom: env(safe-area-inset-bottom)`을 갖지만, `.toastRegion`
 * (overlay.module.css)의 `bottom` 계산식(`calc(var(--space-6) +
 * var(--fixed-bottom-inset, 0px))`)에는 `env(safe-area-inset-bottom)` 항이 아예
 * 없다. 6곳 중 이 한 곳만 빠져 있다 — `viewportFit:"cover"`를 켜지 않는 현재
 * 정책에서는 `env()`가 항상 0으로 해소되어 잠들어 있지만, 정책이 바뀌면 노치/홈
 * 인디케이터가 있는 기기에서 토스트가 안전영역보다 낮게 표시될 수 있다.
 *
 * **이 테스트는 지금 실패해야 정상이다(red).** `.toastRegion`의 `bottom` 계산식에
 * `env(safe-area-inset-bottom)`이 추가되면 통과해야 한다.
 */
test.use({ viewport: { width: 390, height: 844 } });

test.describe("토스트 영역이 다른 하단 고정 UI와 같은 safe-area 계약을 따른다", () => {
  test(".toastRegion의 bottom 계산식에 safe-area-inset-bottom이 포함된다", async ({ page }) => {
    test.fail(
      true,
      "KF-06: .toastRegion에 safe-area-inset-bottom 누락 — 근본 수정 전까지 알려진 실패",
    );
    await page.goto("/saved", { waitUntil: "networkidle" });

    const bottomValue = await page.evaluate(() => {
      const regionOrNull = document.querySelector<HTMLElement>(
        '[aria-live="polite"][class*="toast"]',
      );
      if (regionOrNull === null) return null;
      const region: HTMLElement = regionOrNull;

      // 이 프로젝트의 CSS는 `@layer components { ... }`로 감싸져 있다(cascade
      // layer 규율). `@layer` 블록은 CSSLayerBlockRule이고 최상위 cssRules를 평평하게
      // 훑으면 그 안의 CSSStyleRule을 절대 못 찾는다 — 그룹 규칙(@layer/@media/
      // @supports 등)은 재귀적으로 내려가야 한다.
      function findBottomRule(rules: CSSRuleList): string | null {
        for (const rule of Array.from(rules)) {
          if (
            rule instanceof CSSStyleRule &&
            region.matches(rule.selectorText) &&
            rule.style.bottom !== ""
          ) {
            return rule.style.bottom;
          }
          if ("cssRules" in rule && rule.cssRules) {
            const nested = findBottomRule((rule as CSSGroupingRule).cssRules);
            if (nested !== null) return nested;
          }
        }
        return null;
      }

      // getComputedStyle은 계산된 px 값만 주므로, calc() 안에 env()가 있는지는
      // 소스 스타일시트를 직접 뒤져야 확인 가능하다.
      for (const sheet of Array.from(document.styleSheets)) {
        let rules: CSSRuleList;
        try {
          rules = sheet.cssRules;
        } catch {
          continue;
        }
        const found = findBottomRule(rules);
        if (found !== null) return found;
      }
      return null;
    });

    expect(bottomValue, "toastRegion의 bottom 규칙을 찾지 못함").not.toBeNull();
    expect(bottomValue).toContain("safe-area-inset-bottom");
  });
});
