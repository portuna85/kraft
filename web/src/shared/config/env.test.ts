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

// TD-010: ad-unit.tsx/layout.tsx/frequency/page.tsx/revalidate/route.ts가 직접 읽던
// process.env를 env.ts로 옮겼다 — 그 새 필드들이 값 유무에 따라 올바르게 해석/폴백되는지 확인.
describe("publicEnv / serverEnv — TD-010 신규 필드", () => {
  const AD_VARS = [
    "NEXT_PUBLIC_KAKAO_ADFIT_UNIT_FREQUENCY",
    "NEXT_PUBLIC_KAKAO_ADFIT_UNIT_FREQUENCY_DESKTOP",
    "NEXT_PUBLIC_KAKAO_ADFIT_UNIT_STICKY",
    "NEXT_PUBLIC_ADSENSE_RESERVE_PLACEHOLDER",
    "NEXT_PUBLIC_ADSENSE_CLIENT_ID",
    "NEXT_PUBLIC_ADSENSE_UNIT_FREQUENCY",
    "NEXT_PUBLIC_ADSENSE_UNIT_FREQUENCY_MOBILE",
    "NEXT_PUBLIC_ADSENSE_UNIT_SIDEBAR",
    "KRAFT_REVALIDATE_SECRET",
  ] as const;

  beforeEach(() => {
    for (const key of AD_VARS) delete process.env[key];
  });

  afterEach(() => {
    for (const key of AD_VARS) delete process.env[key];
  });

  async function loadEnv() {
    vi.resetModules();
    return import("./env");
  }

  it("모두 미설정이면 빈 문자열/false/undefined로 안전하게 폴백한다", async () => {
    const { publicEnv, serverEnv } = await loadEnv();

    expect(publicEnv.kakaoAdfitUnitFrequency).toBe("");
    expect(publicEnv.kakaoAdfitUnitFrequencyDesktop).toBe("");
    expect(publicEnv.kakaoAdfitUnitSticky).toBe("");
    expect(publicEnv.adsenseReservePlaceholder).toBe(false);
    expect(publicEnv.adsenseClientId).toBe("");
    expect(publicEnv.adsenseUnitFrequency).toBeUndefined();
    expect(publicEnv.adsenseUnitFrequencyMobile).toBeUndefined();
    expect(publicEnv.adsenseUnitSidebar).toBe("");
    expect(serverEnv.revalidateSecret).toBeUndefined();
  });

  it("설정된 값을 그대로 반영한다", async () => {
    process.env.NEXT_PUBLIC_KAKAO_ADFIT_UNIT_FREQUENCY = "unit-mobile";
    process.env.NEXT_PUBLIC_KAKAO_ADFIT_UNIT_FREQUENCY_DESKTOP = "unit-desktop";
    process.env.NEXT_PUBLIC_KAKAO_ADFIT_UNIT_STICKY = "unit-sticky";
    process.env.NEXT_PUBLIC_ADSENSE_RESERVE_PLACEHOLDER = "true";
    process.env.NEXT_PUBLIC_ADSENSE_CLIENT_ID = "ca-pub-123";
    process.env.NEXT_PUBLIC_ADSENSE_UNIT_FREQUENCY = "slot-desktop";
    process.env.NEXT_PUBLIC_ADSENSE_UNIT_FREQUENCY_MOBILE = "slot-mobile";
    process.env.NEXT_PUBLIC_ADSENSE_UNIT_SIDEBAR = "slot-sidebar";
    process.env.KRAFT_REVALIDATE_SECRET = "shh";

    const { publicEnv, serverEnv } = await loadEnv();

    expect(publicEnv.kakaoAdfitUnitFrequency).toBe("unit-mobile");
    expect(publicEnv.kakaoAdfitUnitFrequencyDesktop).toBe("unit-desktop");
    expect(publicEnv.kakaoAdfitUnitSticky).toBe("unit-sticky");
    expect(publicEnv.adsenseReservePlaceholder).toBe(true);
    expect(publicEnv.adsenseClientId).toBe("ca-pub-123");
    expect(publicEnv.adsenseUnitFrequency).toBe("slot-desktop");
    expect(publicEnv.adsenseUnitFrequencyMobile).toBe("slot-mobile");
    expect(publicEnv.adsenseUnitSidebar).toBe("slot-sidebar");
    expect(serverEnv.revalidateSecret).toBe("shh");
  });

  it("RESERVE_PLACEHOLDER는 문자열 \"true\"가 아니면 false로 취급한다", async () => {
    process.env.NEXT_PUBLIC_ADSENSE_RESERVE_PLACEHOLDER = "1";
    const { publicEnv } = await loadEnv();

    expect(publicEnv.adsenseReservePlaceholder).toBe(false);
  });
});
