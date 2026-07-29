import { test, expect } from "@playwright/test";
import { gotoAndWaitForRealContent } from "../lib/goto-real-content";

// 커뮤니티 공개 목록·상세는 ISR로 캐시되므로, 서버가 내려주는 초기 HTML에
// 로그인 상태·개인화 정보가 절대 섞여 들어가지 않아야 한다(그래야 서로 다른 사용자가 같은
// 캐시된 HTML을 봐도 안전하다). 로그인 상태는 클라이언트가 /api/v1/community/session을
// no-store로 별도 조회해서만 얻는다 — 이 테스트는 그 경계가 실제로 지켜지는지 증명한다.
test("커뮤니티 목록 ISR HTML에는 세션·로그인 정보가 포함되지 않는다", async ({ page }) => {
  await gotoAndWaitForRealContent(page, "/community");

  const html = await page.content();
  expect(html).not.toContain("loggedIn");
  expect(html).toContain("테스트 게시글");
});

test("커뮤니티 상세 ISR HTML에는 세션·로그인 정보가 포함되지 않는다", async ({ page }) => {
  await gotoAndWaitForRealContent(page, "/community/posts/1");

  const html = await page.content();
  expect(html).not.toContain("loggedIn");
  expect(html).toContain("테스트 게시글");
  await expect(page.getByRole("heading", { name: "테스트 게시글" })).toBeVisible();
});

test("세션 쿠키가 있어도 커뮤니티 상세 HTML에 개인화 정보가 섞이지 않는다", async ({ browser }) => {
  // 서버 컴포넌트는 백엔드 세션 API를 호출하지 않으므로(로그인 상태는 클라이언트가 별도
  // no-store로 조회) 브라우저에 세션 쿠키가 있어도 SSR 단계에서는 아무 영향이 없어야 한다 —
  // 두 렌더가 완전히 동일한 바이트열일 필요는 없지만(빌드 자산 참조 순서 등은 결정적이지
  // 않을 수 있음), 세션/로그인 마커가 여전히 없어야 한다는 것이 실제 안전 속성이다.
  const context = await browser.newContext();
  await context.addCookies([
    { name: "JSESSIONID", value: "fake-session-value", url: "http://127.0.0.1:3101" },
  ]);
  const otherPage = await context.newPage();
  await gotoAndWaitForRealContent(otherPage, "/community/posts/1");
  const html = await otherPage.content();
  await context.close();

  expect(html).not.toContain("loggedIn");
  expect(html).not.toContain("fake-session-value");
  expect(html).toContain("테스트 게시글");
});
