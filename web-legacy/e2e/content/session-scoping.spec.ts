import { test, expect } from "@playwright/test";
import { gotoAndWaitForRealContent } from "../lib/goto-real-content";

// KF-05: CommunitySessionProvider가 라우트를 스코핑하는지 실제 네트워크 요청으로 검증한다.
// 익명 공개 페이지(홈·통계 등)에서는 /api/v1/community/session이 아예 나가지 않아야 하고,
// /community 진입 시에는 정확히 1회 나가야 한다 — 인위로 스코핑을 깨보면(항상 호출하도록
// 되돌리면) 첫 번째 그룹이 반드시 실패해야 이 단언이 실제로 회귀를 잡는다는 뜻이다.
function trackSessionRequests(page: import("@playwright/test").Page): string[] {
  const urls: string[] = [];
  page.on("request", (request) => {
    if (request.url().includes("/api/v1/community/session")) {
      urls.push(request.url());
    }
  });
  return urls;
}

for (const path of ["/", "/stats", "/frequency", "/companion"]) {
  test(`${path} — 익명 공개 페이지는 세션 API를 호출하지 않는다`, async ({ page }) => {
    const sessionRequests = trackSessionRequests(page);

    await gotoAndWaitForRealContent(page, path);

    expect(sessionRequests).toHaveLength(0);
  });
}

test("/community — 진입 시 세션 API를 정확히 1회 호출한다", async ({ page }) => {
  const sessionRequests = trackSessionRequests(page);

  await gotoAndWaitForRealContent(page, "/community");

  expect(sessionRequests).toHaveLength(1);
});
