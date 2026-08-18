import { expect, type Locator, type Page } from "@playwright/test";

/**
 * `aria-disabled`/`pointer-events:none`만으로는 네이티브 `<a href>`의 키보드 활성화를
 * 막지 못한다 — Enter를 누르면 여전히 이동한다. URL 동일성 비교로는 이걸 못 잡는다
 * (비활성 링크가 이미 현재 페이지를 가리키는 경우가 흔해서다 — 예: 첫 페이지의
 * "처음"/"이전"). `framenavigated` 이벤트 발생 여부로 실제 네비게이션이 일어났는지를
 * 직접 확인한다.
 */
export async function assertNoNavigationOnKeyboardActivation(page: Page, locator: Locator) {
  let navigated = false;
  const onNavigated = () => {
    navigated = true;
  };
  page.on("framenavigated", onNavigated);

  try {
    await locator.focus();
    await page.keyboard.press("Enter");
    await page.waitForTimeout(300);
  } finally {
    page.off("framenavigated", onNavigated);
  }

  expect(navigated, "비활성 상태의 링크가 Enter 키로 네비게이션을 일으켰다").toBe(false);
}
