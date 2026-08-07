import { describe, expect, it, vi } from "vitest";

vi.mock("@/lib/api", () => ({
  getPublicBaseUrl: () => "https://kraft-lotto.example",
  getLatestWinningNumber: vi.fn().mockResolvedValue({ drawDate: "2026-01-03" }),
}));

// L-10: /community의 lastModified가 로또 최신 회차 발표일(콘텐츠와 무관)을 재사용하고
// 있었다 — 검색엔진에 잘못된 신선도 신호를 준다. 실제 타임스탬프 전까지는 생략해야 한다.
describe("sitemap /community lastModified", () => {
  it("/community는 lastModified를 생략하고 다른 라우트는 최신 회차 발표일을 쓴다", async () => {
    const sitemap = (await import("@/app/sitemap")).default;
    const entries = await sitemap();

    const community = entries.find((entry) => entry.url.endsWith("/community"));
    const home = entries.find((entry) => entry.url.endsWith("/"));

    expect(community?.lastModified).toBeUndefined();
    expect(home?.lastModified).toBe("2026-01-03T00:00:00+09:00");
  });
});
