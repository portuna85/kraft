import type { Metadata } from "next";

/**
 * 환경 변수 접근 지점
 *
 * process.env를 코드 곳곳에서 직접 읽으면 어떤 변수가 필요한지 알 수 없고, 서버 전용
 * 값이 클라이언트 번들에 섞여 들어가도 눈에 띄지 않는다. 여기서만 읽는다.
 */

/** 서버에서만 읽는다. 클라이언트 컴포넌트에서 import하면 값이 비어 있다. */
export const serverEnv = {
  backendInternalUrl: process.env.KRAFT_BACKEND_INTERNAL_URL ?? "http://backend:8080",
  /**
   * /ops 호스트 게이트. **미설정은 "제한 없음"이 아니라 "차단"이다** — 과거 fail-open
   * 이던 시절 /ops가 공개 도메인에서 그대로 열려 있었다(레거시 F-P0-12).
   */
  opsAllowedHost: process.env.KRAFT_OPS_ALLOWED_HOST ?? null,
  /** /api/revalidate 웹훅 인증 시크릿. Route Handler에서만 쓰이며 클라이언트 번들에
   * 들어가지 않는다. */
  revalidateSecret: process.env.KRAFT_REVALIDATE_SECRET,
} as const;

/** 빌드 시점에 인라인되는 공개 값. 비밀을 넣지 않는다. */
export const publicEnv = {
  baseUrl: process.env.KRAFT_PUBLIC_BASE_URL ?? "http://localhost:3000",
  adNetwork: process.env.NEXT_PUBLIC_AD_NETWORK === "adsense" ? "adsense" : "adfit",
  googleSiteVerification: process.env.NEXT_PUBLIC_GOOGLE_SITE_VERIFICATION,
  naverSiteVerification: process.env.NEXT_PUBLIC_NAVER_SITE_VERIFICATION,
  kakaoAdfitUnitFrequency: process.env.NEXT_PUBLIC_KAKAO_ADFIT_UNIT_FREQUENCY ?? "",
  kakaoAdfitUnitFrequencyDesktop: process.env.NEXT_PUBLIC_KAKAO_ADFIT_UNIT_FREQUENCY_DESKTOP ?? "",
  kakaoAdfitUnitSticky: process.env.NEXT_PUBLIC_KAKAO_ADFIT_UNIT_STICKY ?? "",
  adsenseReservePlaceholder: process.env.NEXT_PUBLIC_ADSENSE_RESERVE_PLACEHOLDER === "true",
  adsenseClientId: process.env.NEXT_PUBLIC_ADSENSE_CLIENT_ID ?? "",
  adsenseUnitFrequency: process.env.NEXT_PUBLIC_ADSENSE_UNIT_FREQUENCY,
  adsenseUnitFrequencyMobile: process.env.NEXT_PUBLIC_ADSENSE_UNIT_FREQUENCY_MOBILE,
  adsenseUnitSidebar: process.env.NEXT_PUBLIC_ADSENSE_UNIT_SIDEBAR ?? "",
} as const;

export type AdNetwork = (typeof publicEnv)["adNetwork"];

/**
 * 검색엔진 사이트 소유권 확인 메타태그 — web-legacy 승계.
 *
 * `publicEnv`에서 파생되는 순수 값이라 여기 둔다 — `RootLayout`(next/font·
 * next/headers를 모듈 스코프에서 실행)과 분리해 두면 이 값만 단위 테스트할 수 있다.
 */
export const siteVerificationMetadata: Metadata["verification"] = {
  google: publicEnv.googleSiteVerification,
  other: publicEnv.naverSiteVerification
    ? { "naver-site-verification": publicEnv.naverSiteVerification }
    : undefined,
};
