import { test, expect, type Page } from "@playwright/test";
import { expectNoOverflow } from "../lib/expect-no-overflow";
import { gotoAndWaitForRealContent } from "../lib/goto-real-content";

// §6-5: 브라우저/OS의 "텍스트 크기 확대" 접근성 설정(WCAG 1.4.4)을 흉내낸다. 실제 페이지
// 줌(Ctrl/Cmd +)과 달리 레이아웃 컨테이너는 그대로 두고 텍스트만 커지는 상황이라, 오히려
// 이쪽이 "라벨·값이 잘리거나 겹치는" 실패 유형을 더 정확히 재현한다. html의 font-size를
// 200%로 올려 rem 기반 텍스트는 커지되 px 기반 그리드 칸은 그대로인 상태를 만든다.
//
// 처음엔 "이웃 그리드 칸의 경계를 넘지 않는지"를 봤는데, CSS Grid는 애초에 칸이 서로
// 겹치지 않게 설계돼 있어(text는 자기 칸 안에서 줄바꿈될 뿐) 400%로 올려도 항상 통과하는
// 무의미한 검사였다 — 진짜 위험은 white-space: nowrap이 걸린 요소(.prize-table-rank 등)가
// 텍스트가 커지면서 자기 자신 안에서 잘리는 것이다. 요소 자신의 scrollWidth가 clientWidth를
// 넘는지(내부 클리핑)로 바꿔서 실제로 의미 있는 검사가 되게 했다.
async function setTextZoom200(page: Page) {
  await page.addStyleTag({ content: "html { font-size: 200% !important; }" });
}

// .balls/.prize-table-wrap처럼 의도된 가로 스크롤 컨테이너는 data-allow-overflow로
// 이미 표시돼 있다(e2e/lib/expect-no-overflow.ts와 동일한 관례) — 그 안의 텍스트가
// 스크롤 컨테이너보다 넓어지는 건 잘림이 아니라 정상 동작이므로 제외한다.
async function expectNoInternalTextClipping(page: Page) {
  const clipped = await page.evaluate(() =>
    [...document.querySelectorAll("body *")]
      .filter((el) => el.closest("[data-allow-overflow]") === null)
      .filter((el) => el.children.length === 0 && (el.textContent ?? "").trim().length > 0)
      .filter((el) => el.scrollWidth > el.clientWidth + 1)
      .map((el) => ({ class: el.className || el.tagName, text: (el.textContent ?? "").slice(0, 30) })),
  );
  expect(clipped).toEqual([]);
}

test("200% 텍스트 확대 — /stats 패턴 항목이 내부에서 잘리지 않는다", async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 });
  await gotoAndWaitForRealContent(page, "/stats");
  await setTextZoom200(page);

  await expect(page.getByTestId("pattern-list").first()).toBeVisible();
  await expectNoInternalTextClipping(page);
  await expectNoOverflow(page);
});

test("200% 텍스트 확대 — /frequency 출현 통계도 잘리거나 넘치지 않는다", async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 });
  await gotoAndWaitForRealContent(page, "/frequency");
  await setTextZoom200(page);

  await expect(page.locator(".freq-summary")).toBeVisible();
  await expectNoInternalTextClipping(page);
  await expectNoOverflow(page);
});

test("200% 텍스트 확대 — 모바일(390px)에서도 홈이 잘리거나 넘치지 않는다", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 900 });
  await gotoAndWaitForRealContent(page, "/");
  await setTextZoom200(page);

  await expect(page.locator(".result-panel .balls")).toBeVisible();
  await expectNoInternalTextClipping(page);
  await expectNoOverflow(page);
});

test("200% 텍스트 확대 — 모바일(390px)에서 커뮤니티 상세가 잘리거나 넘치지 않는다", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 900 });
  await page.route("**/api/v1/community/posts/1/comments*", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ topLevel: [], totalTopLevelComments: 0, page: 0, totalPages: 0 }),
    })
  );
  await gotoAndWaitForRealContent(page, "/community/posts/1");
  await setTextZoom200(page);

  await expect(page.getByRole("heading", { name: "테스트 게시글" })).toBeVisible();
  await expectNoInternalTextClipping(page);
  await expectNoOverflow(page);
});
