import { test, expect } from "@playwright/test";
import { gotoAndWaitForRealContent } from "../lib/goto-real-content";

// RW-P1-06: Windows 고대비(forced-colors) 모드에서는 커스텀 배경·테두리·box-shadow가
// 시스템 팔레트로 치환되거나 아예 사라진다(box-shadow는 보통 렌더링되지 않음). 이 스펙은
// 색상만으로 선택 상태를 표시하던 요소들이 forced-colors 모드에서도 outline으로 구조적으로
// 구분되는지 검증한다. Playwright는 page.emulateMedia({ forcedColors })로 이 미디어 특성을
// 직접 에뮬레이트할 수 있다.

test("고대비 모드 — /frequency 선택된 필터 탭이 outline으로 구분된다", async ({ page }) => {
  await gotoAndWaitForRealContent(page, "/frequency");
  await page.emulateMedia({ forcedColors: "active" });

  const activeTab = page.getByRole("button", { name: "전체" });
  const inactiveTab = page.getByRole("button", { name: "최근 100회" });

  await expect(activeTab).toHaveClass(/active/);
  const activeOutline = await activeTab.evaluate((el) => getComputedStyle(el).outlineStyle);
  const inactiveOutline = await inactiveTab.evaluate((el) => getComputedStyle(el).outlineStyle);

  expect(activeOutline).not.toBe("none");
  expect(inactiveOutline).toBe("none");
});

test("고대비 모드 — /companion 선택된 번호 공이 outline으로 구분된다", async ({ page }) => {
  await gotoAndWaitForRealContent(page, "/companion");

  const firstBall = page.locator("button.ball").first();
  await firstBall.click();
  await page.emulateMedia({ forcedColors: "active" });

  const outline = await firstBall.evaluate((el) => getComputedStyle(el).outlineStyle);
  expect(outline).not.toBe("none");
});
