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

/**
 * RSP-31(docs/improvement.md): 지금까지 `assertNotOccludedByFixedUi`의 유일한
 * 호출부는 `/`의 footer 마지막 링크 하나였다 — "마지막 요소"만 볼 뿐 사용자가
 * 실제로 키보드로 도달하는 본문 컨트롤은 한 번도 이 단언을 거치지 않았다.
 * `e2e/a11y/keyboard-flows.spec.ts`가 이미 Tab으로 도달 가능하다고 검증해 둔
 * 두 대상(추천 생성 버튼, 댓글 신고 다이얼로그 트리거)이 하단 탭바에 가려지지
 * 않는지를 같은 모바일 뷰포트에서 추가로 확인한다 — 전체 라우트·전체 상태
 * 매트릭스가 아니라 이미 키보드 도달성이 증명된 두 지점만 다룬다.
 */
test.describe("키보드로 도달하는 본문 컨트롤이 고정 UI에 가려지지 않는다", () => {
  test.use({ viewport: { width: 390, height: 844 } });

  test("/recommend의 조합 만들기 버튼", async ({ page }) => {
    await page.goto("/recommend", { waitUntil: "networkidle" });
    const button = page.getByRole("button", { name: "조합 만들기" });
    await button.scrollIntoViewIfNeeded();
    await assertNotOccludedByFixedUi(page, 'button:has-text("조합 만들기")', [
      'nav[aria-label="바로가기"]',
    ]);
  });

  test("/community/posts/1의 신고 버튼", async ({ page }) => {
    await page.goto("/community/posts/1", { waitUntil: "networkidle" });
    const button = page.getByRole("button", { name: "이 글 신고" });
    await button.scrollIntoViewIfNeeded();
    await assertNotOccludedByFixedUi(page, 'button:has-text("이 글 신고")', [
      'nav[aria-label="바로가기"]',
    ]);
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
