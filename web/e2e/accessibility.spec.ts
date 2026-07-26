import { test, expect } from "@playwright/test";
import { expectNoA11yViolations } from "./lib/expect-no-a11y-violations";

// F-01: 백엔드가 없다는 전제(§6-2 기본 트랙)에서도 실제로 사용자가 보는 화면 —
// 폼 전용 페이지, 에러/폴백 경계, 404 — 을 axe로 스캔한다. 실콘텐츠 상태(홈·통계
// 등)는 픽스처 백엔드가 있는 content 트랙(e2e/content/accessibility.spec.ts)이 맡는다.
const PAGES: Array<{ name: string; path: string }> = [
  { name: "홈(백엔드 없음 폴백)", path: "/" },
  { name: "번호 추천", path: "/recommend" },
  { name: "번호 분석", path: "/analysis" },
  { name: "저장 번호(조회 실패 상태)", path: "/saved" },
  { name: "커뮤니티 목록(백엔드 없음 폴백)", path: "/community" },
  { name: "서비스 상태(폴백)", path: "/status" },
  { name: "404", path: "/no-such-route-xyz" },
];

for (const { name, path } of PAGES) {
  test(`${name} — 라이트 모드 접근성 위반 없음`, async ({ page }) => {
    await page.goto(path);
    await expectNoA11yViolations(page);
  });

  test(`${name} — 다크 모드 접근성 위반 없음`, async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.setItem("kraft-theme", "dark");
    });
    await page.goto(path);
    await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
    await expectNoA11yViolations(page);
  });
}

test("운영 대시보드(토큰 미입력) — 접근성 위반 없음", async ({ page }) => {
  await page.goto("/ops");
  await expectNoA11yViolations(page);
});

test("운영 대시보드(운영 상태 조회 결과 표시) — 접근성 위반 없음", async ({ page }) => {
  await page.route("**/ops-api/summary", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        service: "kraft-lotto",
        timezone: "Asia/Seoul",
        status: "정상",
        latestRound: 1230,
        latestDrawDate: "2026-01-03",
        checkedAt: "2026-01-03T12:00:00Z",
        fresh: true,
      }),
    })
  );

  await page.goto("/ops");
  await page.getByPlaceholder("X-Ops-Token 값을 입력하세요").fill("token");
  await page.getByRole("button", { name: "운영 상태 확인" }).click();
  await expect(page.getByText("운영 상태를 불러왔습니다.")).toBeVisible();

  await expectNoA11yViolations(page);
});
