import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

/**
 * `siteVerificationMetadata`는 `publicEnv`처럼 모듈 로드 시점에 `process.env`를
 * 한 번 읽어 고정되므로, 값을 바꿔 검증하려면 매번 모듈을 새로 로드해야 한다
 * (`src/proxy.test.ts`와 같은 패턴).
 */
beforeEach(() => {
  delete process.env.NEXT_PUBLIC_GOOGLE_SITE_VERIFICATION;
  delete process.env.NEXT_PUBLIC_NAVER_SITE_VERIFICATION;
});

afterEach(() => {
  delete process.env.NEXT_PUBLIC_GOOGLE_SITE_VERIFICATION;
  delete process.env.NEXT_PUBLIC_NAVER_SITE_VERIFICATION;
});

async function loadSiteVerification() {
  vi.resetModules();
  const mod = await import("./env");
  return mod.siteVerificationMetadata;
}

describe("siteVerificationMetadata", () => {
  it("둘 다 미설정이면 google은 undefined, other는 undefined다", async () => {
    const verification = await loadSiteVerification();

    expect(verification?.google).toBeUndefined();
    expect(verification?.other).toBeUndefined();
  });

  it("구글 값만 설정하면 google에만 반영된다", async () => {
    process.env.NEXT_PUBLIC_GOOGLE_SITE_VERIFICATION = "google-token";
    const verification = await loadSiteVerification();

    expect(verification?.google).toBe("google-token");
    expect(verification?.other).toBeUndefined();
  });

  it("네이버 값만 설정하면 naver-site-verification 키로 반영된다", async () => {
    process.env.NEXT_PUBLIC_NAVER_SITE_VERIFICATION = "naver-token";
    const verification = await loadSiteVerification();

    expect(verification?.other).toEqual({ "naver-site-verification": "naver-token" });
  });

  it("둘 다 설정하면 둘 다 반영된다", async () => {
    process.env.NEXT_PUBLIC_GOOGLE_SITE_VERIFICATION = "google-token";
    process.env.NEXT_PUBLIC_NAVER_SITE_VERIFICATION = "naver-token";
    const verification = await loadSiteVerification();

    expect(verification?.google).toBe("google-token");
    expect(verification?.other).toEqual({ "naver-site-verification": "naver-token" });
  });
});
