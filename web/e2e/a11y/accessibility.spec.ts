import { expect, test } from "@playwright/test";

import { expectNoA11yViolations } from "../lib/expect-no-a11y-violations";

/**
 * axe 자동 검사 — T-23, improvement_fe.md §21.4
 *
 * default 트랙과 달리 여기서는 픽스처가 채워 준 실제 콘텐츠 상태(회차·통계·게시글·
 * 댓글)를 라이트·다크 두 테마로 스캔한다. 목록/폼처럼 상태가 비어 있는 화면은
 * 커버되지 않는다 — 그 조합은 legacy가 이미 검증한 값과 크게 다르지 않다고 보고
 * 여기서는 실콘텐츠에 집중한다(§21.4 범위).
 */
const PAGES: Array<{ name: string; path: string }> = [
  { name: "홈", path: "/" },
  { name: "번호 추천", path: "/recommend" },
  { name: "추천 이력", path: "/recommend/history" },
  { name: "번호 분석", path: "/analysis?numbers=1,8,17,24,33,41" },
  { name: "보관함", path: "/saved" },
  { name: "번호별 출현", path: "/frequency" },
  { name: "당첨 패턴", path: "/stats" },
  { name: "함께 나온 번호", path: "/companion" },
  { name: "커뮤니티 목록", path: "/community" },
  { name: "커뮤니티 글쓰기", path: "/community/write" },
  { name: "게시글 상세(댓글·답글·tombstone 포함)", path: "/community/posts/1" },
  { name: "서비스 상태", path: "/status" },
  { name: "자주 묻는 질문", path: "/info/faq" },
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
