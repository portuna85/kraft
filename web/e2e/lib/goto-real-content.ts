import { expect, type Page } from "@playwright/test";

// /companion, /frequency, /stats는 각각 route-level loading.tsx 스켈레톤을 두고 있는데,
// 실콘텐츠와 같은 CSS Module(companion.module.css 등)을 그대로 재사용한다. networkidle까지
// 기다려도 스트리밍 전환 프레임에 스켈레톤 li와 실콘텐츠 li가 잠깐 함께 DOM에 남는 걸
// 실제로 확인했다 — 스켈레톤 shimmer 요소(.skeleton-line/.skeleton-ball)가 완전히
// 사라졌는지까지 명시적으로 기다린다. §6-2 콘텐츠 트랙 전용(픽스처 백엔드 전제).
export async function gotoAndWaitForRealContent(page: Page, url: string) {
  await page.goto(url, { waitUntil: "networkidle" });
  await expect(page.locator(".skeleton-line, .skeleton-ball")).toHaveCount(0);
}
