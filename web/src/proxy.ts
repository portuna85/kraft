import { NextRequest, NextResponse } from "next/server";

const opsAllowedHost = process.env.KRAFT_OPS_ALLOWED_HOST;

// Next.js App Router는 CSP에 nonce가 있을 때만 자신이 스트리밍하는 RSC
// 하이드레이션용 inline script에도 그 nonce를 자동으로 붙여준다(공식 문서 패턴).
// sha256 해시만으로는 우리가 작성한 inline script(테마 초기화, JSON-LD)는 허용되지만
// Next가 내부적으로 주입하는, 페이지마다 내용이 달라지는 RSC 스크립트는 허용할 수 없어
// 모든 페이지에서 하이드레이션이 CSP에 막힌다(실측: /recommend, /saved 등에서 확인).
// 그래서 nonce는 전 경로에 매 요청 발급한다.
// ISR 회복 대안: Caddy에 script-src 'unsafe-inline' 정적 CSP로 교체하는 방식이 있으나
// 보안 트레이드오프로 보류. 현재는 Caddy에서 max-age=60 브라우저 SWR을 적용해
// 재방문 비용을 낮추는 것으로 대체.
export function generateNonce(): string {
  const array = new Uint8Array(16);
  crypto.getRandomValues(array);
  return Buffer.from(array).toString("base64");
}

// ad-unit.tsx의 네트워크 판정과 동일한 기준 — 슬롯당 한 네트워크만 렌더하므로(주석 참고)
// CSP도 실제로 스크립트를 로드하는 네트워크의 출처만 허용한다.
const isAdSense = process.env.NEXT_PUBLIC_AD_NETWORK === "adsense";

const AD_SCRIPT_SRC = isAdSense
  ? "https://pagead2.googlesyndication.com"
  : "https://t1.kakaocdn.net";
const AD_IMG_SRC = isAdSense
  ? "https://*.googlesyndication.com https://*.g.doubleclick.net"
  : "https://*.daumcdn.net https://*.kakao.com https://*.kakaocdn.net";
const AD_CONNECT_SRC = isAdSense
  ? "https://pagead2.googlesyndication.com https://googleads.g.doubleclick.net"
  : "https://*.kakao.com https://*.daumcdn.net https://*.kakaocdn.net";
const AD_FRAME_SRC = isAdSense
  ? "https://googleads.g.doubleclick.net https://tpc.googlesyndication.com"
  : "https://t1.kakaocdn.net https://*.kakao.com";

export function buildCsp(nonce: string): string {
  const isDev = process.env.NODE_ENV !== "production";
  return [
    `default-src 'self'`,
    `script-src 'self' 'nonce-${nonce}' ${AD_SCRIPT_SRC}${isDev ? " 'unsafe-eval'" : ""}`,
    `style-src 'self' 'unsafe-inline'`,
    `img-src 'self' data: ${AD_IMG_SRC}`,
    `font-src 'self'`,
    `connect-src 'self' ${AD_CONNECT_SRC}`,
    `frame-src ${AD_FRAME_SRC}`,
    `object-src 'none'`,
    `base-uri 'self'`,
    `form-action 'self'`,
    `frame-ancestors 'none'`,
  ].join("; ");
}

export function proxy(req: NextRequest) {
  const { pathname } = req.nextUrl;

  if (pathname.startsWith("/ops")) {
    if (opsAllowedHost) {
      const host = req.headers.get("host") ?? "";
      const hostname = host.split(":")[0];
      if (hostname !== opsAllowedHost) {
        return NextResponse.rewrite(new URL("/not-found", req.url), { status: 404 });
      }
    }
  }

  const nonce = generateNonce();
  const csp = buildCsp(nonce);

  const requestHeaders = new Headers(req.headers);
  requestHeaders.set("x-nonce", nonce);

  const response = NextResponse.next({ request: { headers: requestHeaders } });
  response.headers.set("Content-Security-Policy", csp);

  return response;
}

export const config = {
  matcher: [
    "/ops/:path*",
    "/((?!_next/static|_next/image|favicon\\.ico).*)",
  ],
};
