import type { AdNetwork } from "./env";

/**
 * CSP 정책·§20.3, §24.2(8: Caddy와 협의된 정책은 계약)
 *
 * **nonce는 전 경로에 매 요청 발급한다.** Next App Router는 CSP에 nonce가 있을 때만
 * 자신이 스트리밍하는 RSC 하이드레이션 inline script에도 그 nonce를 붙인다. 이 스크립트는
 * 페이지마다 내용이 달라 sha256 해시로 허용할 수 없으므로, nonce가 없으면 전 페이지에서
 * 하이드레이션이 CSP에 막힌다(레거시에서 /recommend·/saved로 실측된 사실이다).
 * 정적 해시로 대체하려는 시도는 이미 한 번 실패했다 — 반복하지 않는다.
 *
 * 광고 출처는 실제로 스크립트를 로드하는 네트워크 하나만 연다. 두 네트워크를 동시에
 * 열어두면 쓰지 않는 출처가 상시 허용된 채로 남는다.
 */

/** proxy가 nonce를 RSC로 넘길 때 쓰는 요청 헤더 이름. */
export const NONCE_HEADER = "x-nonce";

type AdOrigins = { script: string; img: string; connect: string; frame: string };

const AD_ORIGINS: Record<AdNetwork, AdOrigins> = {
  adsense: {
    script: "https://pagead2.googlesyndication.com",
    img: "https://*.googlesyndication.com https://*.g.doubleclick.net",
    connect: "https://pagead2.googlesyndication.com https://googleads.g.doubleclick.net",
    frame: "https://googleads.g.doubleclick.net https://tpc.googlesyndication.com",
  },
  adfit: {
    script: "https://t1.kakaocdn.net",
    img: "https://*.daumcdn.net https://*.kakao.com https://*.kakaocdn.net",
    connect: "https://*.kakao.com https://*.daumcdn.net https://*.kakaocdn.net",
    frame: "https://t1.kakaocdn.net https://*.kakao.com",
  },
};

export type CspInput = {
  nonce: string;
  adNetwork: AdNetwork;
  /** 개발 모드에서는 Next가 eval을 쓴다 — 프로덕션에서는 절대 켜지 않는다. */
  allowEval: boolean;
  /**
   * FE-SEC-01(docs/improvement.md): `ops`면 광고 네트워크 origin을 CSP에 아예 넣지
   * 않는다. 운영 콘솔이 제3자 광고 네트워크와 완전히 분리돼야 한다는 요구는 컴포넌트
   * 트리 분리(`(ops)/layout.tsx`가 광고 컴포넌트를 렌더하지 않는 것)만으로는
   * 충분하지 않다 — CSP가 origin을 열어 두면 그 자체로 운영 호스트·referrer·접속
   * 패턴이 외부 네트워크에 노출될 수 있는 공격 표면이 남는다.
   */
  surface: "public" | "ops";
};

function directives(
  { nonce, adNetwork, allowEval, surface }: CspInput,
  styleSrc: string,
): string[] {
  const ad = surface === "public" ? AD_ORIGINS[adNetwork] : null;
  return [
    "default-src 'self'",
    `script-src 'self' 'nonce-${nonce}'${ad ? ` ${ad.script}` : ""}${allowEval ? " 'unsafe-eval'" : ""}`,
    `style-src ${styleSrc}`,
    `img-src 'self' data:${ad ? ` ${ad.img}` : ""}`,
    "font-src 'self'",
    `connect-src 'self'${ad ? ` ${ad.connect}` : ""}`,
    `frame-src ${ad ? ad.frame : "'none'"}`,
    "object-src 'none'",
    "base-uri 'self'",
    "form-action 'self'",
    "frame-ancestors 'none'",
  ];
}

/** 실제로 강제되는 정책. */
export function buildCsp(input: CspInput): string {
  return directives(input, "'self' 'unsafe-inline'").join("; ");
}

/**
 * 관측 전용 후보 정책 — style-src에서 'unsafe-inline'을 뺀 버전.
 *
 * 재작성은 이 축소를 실제로 달성할 기회다(§20.3): 인라인 style을 쓰지 않는 컴포넌트만
 * 만들면 위반이 0이 되고, 그때 buildCsp의 style-src를 여기에 맞춰 좁히고 이 함수를
 * 지운다. 실패해도 현행과 같은 수준이라 회귀는 아니다(R-12).
 */
export function buildCspReportOnly(input: CspInput): string {
  return [...directives(input, `'self' 'nonce-${input.nonce}'`), "report-uri /api/csp-report"].join(
    "; ",
  );
}

export function generateNonce(): string {
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  return btoa(String.fromCharCode(...bytes));
}
