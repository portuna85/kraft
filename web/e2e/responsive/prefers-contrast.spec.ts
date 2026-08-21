import { expect, test } from "@playwright/test";

/**
 * RSP-16(docs/improvement.md): `prefers-contrast: more` 대응이 tokens.css의
 * `@media (prefers-contrast: more)` 블록으로 들어갔다 — `--color-border`가
 * 라이트 약 3.05:1, 다크 약 4.47:1로 올라간다(WCAG 1.4.11 비텍스트 3:1 통과).
 *
 * 이 스펙은 픽셀 대비 자체를 재지 않는다 — Playwright는 `page.emulateMedia({
 * contrast: "more" })`로 `prefers-contrast`를 실제로 지원하므로(문서가 요구한
 * DevTools 수동 확인과 별개로), "토큰이 실제로 스위치됐는가"만 자동으로
 * 고정한다. `prefers-reduced-transparency`(RSP-15)는 Playwright에 대응
 * 옵션이 없어 여기서 함께 자동화하지 않는다 — 문서도 그쪽은 수동 확인만
 * 요구한다.
 *
 * 확인 라우트: 표가 가장 많은 `/stats`, 카드가 가장 많은 `/data`(문서 §RSP-16
 * 검증 절차와 동일한 선택).
 */
const ROUTES = ["/stats", "/data"];

async function borderColorOf(page: import("@playwright/test").Page) {
  return page.evaluate(() =>
    getComputedStyle(document.documentElement).getPropertyValue("--color-border").trim(),
  );
}

for (const path of ROUTES) {
  test(`${path}: prefers-contrast:more에서 --color-border가 대비 강화 값으로 바뀐다`, async ({
    page,
  }) => {
    await page.goto(path, { waitUntil: "networkidle" });
    const baseline = await borderColorOf(page);

    await page.emulateMedia({ contrast: "more" });
    const contrasted = await borderColorOf(page);

    expect(contrasted).not.toBe(baseline);
    // 기본값의 알파(0.16)보다 확실히 진해야 한다 — 방향성만 확인한다. 이
    // Chromium 버전은 커스텀 프로퍼티의 rgba()를 getComputedStyle에서
    // 8자리 hex(#RRGGBBAA)로 직렬화해 돌려준다(실측 확인) — 마지막 두 hex
    // 문자가 알파 바이트(0-255)다.
    const alphaOf = (hex: string) => parseInt(hex.slice(-2), 16);
    expect(alphaOf(contrasted)).toBeGreaterThan(alphaOf(baseline));
  });

  test(`${path}: 다크 모드 + prefers-contrast:more에서도 --color-border가 강화된다`, async ({
    page,
  }) => {
    await page.addInitScript(() => {
      localStorage.setItem("kraft-theme", "dark");
    });
    await page.goto(path, { waitUntil: "networkidle" });
    await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
    const baseline = await borderColorOf(page);

    await page.emulateMedia({ contrast: "more" });
    const contrasted = await borderColorOf(page);

    expect(contrasted).not.toBe(baseline);
  });
}
