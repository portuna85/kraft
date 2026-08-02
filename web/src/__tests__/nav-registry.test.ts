import { describe, expect, it } from "vitest";
import { footerLinks, primaryLinks, statusLink } from "@/lib/nav-items";
import { INFO_PAGE_SLUGS } from "@/lib/info-page-metadata";

// FE-007: nav-items.ts가 스스로 "단일 진실 공급원"이라 선언했지만 실제로는 하단 탭,
// STATUS_LINK, 푸터 목록이 각자 라우트를 하드코딩하고 있었다. 라우트를 추가·변경할 때
// 한 곳만 고치면 되는지를 이 테스트가 지킨다.
describe("내비게이션 레지스트리", () => {
  it("푸터는 모든 안내 페이지를 빠짐없이 링크한다", () => {
    const linkedInfoSlugs = footerLinks
      .map((link) => link.href)
      .filter((href) => href.startsWith("/info/"))
      .map((href) => href.replace("/info/", ""))
      .sort();

    expect(linkedInfoSlugs).toEqual([...INFO_PAGE_SLUGS].sort());
  });

  it("푸터에는 서비스 상태 링크가 레지스트리 값 그대로 들어간다", () => {
    expect(footerLinks).toContainEqual(statusLink);
  });

  it("푸터 링크에 중복 경로가 없다", () => {
    const hrefs = footerLinks.map((link) => link.href);
    expect(new Set(hrefs).size).toBe(hrefs.length);
  });

  it("좁은 화면용 짧은 라벨은 원래 라벨보다 길지 않다", () => {
    for (const link of primaryLinks) {
      if (link.shortLabel) {
        expect(link.shortLabel.length).toBeLessThanOrEqual(link.label.length);
      }
    }
  });
});
