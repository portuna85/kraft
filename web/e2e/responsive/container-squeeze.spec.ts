import { expect, test } from "@playwright/test";

import { assertFitsWithoutShrink, assertNoWrap } from "../lib/responsive-assertions";

/**
 * RSP-01 / RSP-03 / RSP-26(docs/improvement.md): 2열 레이아웃이 켜지는 640px
 * **직후**가 안쪽 컨테이너 폭이 가장 좁은 구간이다 — 뷰포트는 넓어졌는데 안쪽
 * 컬럼은 절반이 된다.
 *
 * `/saved`에서 토큰으로 유도한 640px 계산:
 *
 *   뷰포트                                            640px
 *   - .shell padding-inline (--layout-gutter 16 x 2) -> 608px
 *   - .layout 2열, gap 24                            -> (608-24)/2 = 292px
 *   - .list 2열, gap 16                              -> (292-16)/2 = 138px
 *   - Card padding (--space-5 20 x 2) + border 1 x 2  ->  96px  (카드 내부)
 *
 * md 볼 6개는 36 x 6 + 8 x 5 = 256px를 요구하므로 96px 안에서 3줄로 접힌다.
 *
 * **이 결함은 오버플로가 아니라 압착과 래핑이다.** flex/grid 아이템이 min-content
 * 까지 줄고 볼은 wrap되므로 `documentElement.scrollWidth`는 끝까지 `clientWidth`
 * 이하다 — `document-overflow.spec.ts`가 640과 641을 **둘 다** 스윕하면서도
 * 구조적으로 통과시켜 온 이유다.
 */

/** 2열이 켜진 뒤 1024px 재조정 직전까지 — 카드 1장이 가장 좁은 구간. */
const SPLIT_WIDTHS = [640, 700, 800, 1023];

/** 홈 히어로는 1열 구간의 최소 폭도 함께 본다(RSP-26). */
const HOME_WIDTHS = [320, 390, 640, 768, 810, 1023, 1024];

test.describe("2열 전환 직후 컨테이너에서 콘텐츠가 압착되지 않는다", () => {
  // 픽스처의 savedNumbers는 빈 배열로 시작한다 — 시드하지 않으면 이 스펙은 빈
  // 화면을 재고 항상 green이 된다. 전역 상태(POST /api/v1/saved + __test__/reset)로
  // 시드하면 chromium과 firefox-overflow 두 프로젝트가 이 스펙을 동시에 돌면서
  // 서로의 시드를 지운다 — 컨텍스트 쿠키로 시드해 전역을 건드리지 않는다.
  test.beforeEach(async ({ context }) => {
    await context.addCookies([{ name: "e2e-saved", value: "2", url: "http://127.0.0.1:3115" }]);
  });

  for (const width of SPLIT_WIDTHS) {
    test(`${width}px에서 /saved 저장 카드가 압착되지 않는다`, async ({ page }) => {
      await page.setViewportSize({ width, height: 900 });
      await page.goto("/saved", { waitUntil: "networkidle" });

      const ballSets = page.locator('ol[class*="set"]');
      await expect(ballSets.first()).toBeVisible();

      // RSP-01: 한 조합 = 한 줄. 로또 번호의 기본 시각 문법이다.
      await assertNoWrap(page, 'ol[class*="set"]');
      // RSP-03: 날짜와 삭제 버튼이 서로를 min-content까지 밀지 않는다.
      await assertFitsWithoutShrink(page, '[class*="itemHeader"]');
    });
  }
});

test.describe("홈 히어로의 번호 의미 단위가 유지된다", () => {
  for (const width of HOME_WIDTHS) {
    test(`${width}px에서 + 와 보너스 볼이 분리되지 않는다`, async ({ page }) => {
      await page.setViewportSize({ width, height: 900 });
      await page.goto("/", { waitUntil: "networkidle" });

      const plus = page.locator('[class*="plus"]').first();
      if ((await plus.count()) === 0) {
        // 최신 회차가 없는 픽스처 상태 — 볼 자체가 없으므로 검증 대상이 아니다.
        test.skip();
        return;
      }

      // RSP-26: 구분자만 앞줄 끝에 고립되면 "+ 보너스"가 하나의 의미 단위로
      // 읽히지 않는다. 같은 행에 있는지는 두 요소의 top으로 직접 본다.
      const sameRow = await page.evaluate(() => {
        const separator = document.querySelector('[class*="plus"]');
        const bonus = separator?.nextElementSibling?.querySelector('[class*="ball"]');
        if (!separator || !bonus) return null;
        return (
          Math.abs(separator.getBoundingClientRect().top - bonus.getBoundingClientRect().top) < 24
        );
      });
      expect(sameRow, "구분자(+)와 보너스 볼이 서로 다른 줄로 갈라졌다").not.toBe(false);
    });
  }

  for (const width of [640, 768, 810, 1023]) {
    test(`${width}px에서 히어로의 당첨 번호 6개가 한 줄을 유지한다`, async ({ page }) => {
      await page.setViewportSize({ width, height: 900 });
      await page.goto("/", { waitUntil: "networkidle" });

      const ballSet = page.locator('ol[class*="set"]').first();
      if ((await ballSet.count()) === 0) {
        test.skip();
        return;
      }
      // 이 구간에서 히어로는 minmax(260px, 1fr) 컬럼 안이라 뷰포트보다 훨씬 좁다.
      // lotto-ball.module.css:132의 볼 축소는 뷰포트 max-width:420px 기준이라
      // 여기서는 한 번도 켜지지 않는다.
      await assertNoWrap(page, 'ol[class*="set"]');
    });
  }
});
