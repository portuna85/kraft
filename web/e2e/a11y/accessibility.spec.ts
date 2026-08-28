import { expect, test } from "@playwright/test";

import { INFO_PAGE_META } from "@/app/(public)/info/[slug]/metadata";

import { expectNoA11yViolations } from "../lib/expect-no-a11y-violations";

/**
 * axe 자동 검사 — T-23
 *
 * default 트랙과 달리 여기서는 픽스처가 채워 준 실제 콘텐츠 상태(회차·통계·게시글·
 * 댓글)를 라이트·다크 두 테마로 스캔한다. 목록/폼처럼 상태가 비어 있는 화면은
 * 커버되지 않는다 — 그 조합은 legacy가 이미 검증한 값과 크게 다르지 않다고 보고
 * 여기서는 실콘텐츠에 집중한다(§21.4 범위).
 *
 * FE-A11Y-01(docs/improvement.md): /ops·게시글 수정 폼·info 슬러그 8개는 한 번도 스캔되지
 * 않았다. /ops는 토큰 게이트 뒤 클라이언트 컴포넌트라 토큰 없이 열면 자동 fetch가 없는
 * 정적 폼만 렌더된다(ops-console.tsx) — 이 트랙의 픽스처 백엔드가 /ops/* 를 구현하지 않아도
 * 안전하게 스캔할 수 있는 이유다. info 슬러그는 INFO_PAGE_META를 순회해 목록이 라우트
 * 레지스트리(shared/config/routes.ts)를 그대로 따라가게 한다(faq 포함 전부 —
 * 기존에 따로 있던 faq 하드코딩 항목은 중복이라 지웠다).
 */
const PAGES: Array<{ name: string; path: string }> = [
  { name: "홈", path: "/" },
  { name: "번호 추천", path: "/recommend" },
  { name: "번호 분석", path: "/analysis?numbers=1,8,17,24,33,41" },
  { name: "보관함", path: "/saved" },
  { name: "커뮤니티 목록", path: "/community" },
  { name: "커뮤니티 글쓰기", path: "/community/write" },
  { name: "게시글 상세(댓글·답글·tombstone 포함)", path: "/community/posts/1" },
  { name: "서비스 상태", path: "/status" },
  { name: "게시글 수정", path: "/community/posts/1/edit" },
  { name: "운영 콘솔(토큰 미입력)", path: "/ops" },
  ...Object.keys(INFO_PAGE_META).map((slug) => ({
    name: `안내 — ${INFO_PAGE_META[slug as keyof typeof INFO_PAGE_META].title}`,
    path: `/info/${slug}`,
  })),
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
