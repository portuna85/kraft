/**
 * 환경 변수 접근 지점 — improvement_fe.md §20.6
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
} as const;

/** 빌드 시점에 인라인되는 공개 값. 비밀을 넣지 않는다. */
export const publicEnv = {
  baseUrl: process.env.KRAFT_PUBLIC_BASE_URL ?? "http://localhost:3000",
  adNetwork: process.env.NEXT_PUBLIC_AD_NETWORK === "adsense" ? "adsense" : "adfit",
} as const;

export type AdNetwork = (typeof publicEnv)["adNetwork"];
