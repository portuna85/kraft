import { type NextRequest, NextResponse } from "next/server";

import {
  buildCsp,
  buildCspReportOnly,
  generateNonce,
  NONCE_HEADER,
  type CspInput,
} from "@/shared/config/csp";
import { publicEnv, serverEnv } from "@/shared/config/env";

/**
 * /ops 호스트 게이트
 *
 * **미설정이면 차단한다.** 과거 fail-open이라 게이트가 사실상 없는 것과 같았고,
 * /ops가 공개 도메인에서 그대로 열려 있었다(레거시 F-P0-12). 로컬에서 /ops를 쓰려면
 * .env.local에 KRAFT_OPS_ALLOWED_HOST=localhost를 명시해야 한다 — 불편이 아니라 의도다.
 */
function isOpsRequestAllowed(request: NextRequest): boolean {
  const allowedHost = serverEnv.opsAllowedHost;
  if (!allowedHost) return false;

  const hostname = (request.headers.get("host") ?? "").split(":")[0];
  return hostname === allowedHost;
}

export function proxy(request: NextRequest): NextResponse {
  const isOpsRequest = request.nextUrl.pathname.startsWith("/ops");

  if (isOpsRequest && !isOpsRequestAllowed(request)) {
    // 403이 아니라 404다 — 존재 자체를 알리지 않는다.
    return NextResponse.rewrite(new URL("/not-found", request.url), { status: 404 });
  }

  // FE-SEC-01(docs/improvement.md): 호스트 게이트를 이미 통과한 /ops 요청은 여기서
  // "ops" surface로 표시된다 — csp.ts가 광고 네트워크 origin을 아예 열지 않는다.
  const surface: CspInput["surface"] = isOpsRequest ? "ops" : "public";

  const cspInput: CspInput = {
    nonce: generateNonce(),
    adNetwork: publicEnv.adNetwork,
    // TD-010: NODE_ENV는 env.ts가 다루는 앱 설정이 아니라 Next.js/webpack 자체가 주입하는
    // 프레임워크 내장값이라 여기서 직접 읽는 것을 의도적으로 허용한다(proxy.ts는 Edge
    // 런타임 미들웨어라 불필요한 모듈 초기화도 피하는 편이 낫다).
    allowEval: process.env.NODE_ENV !== "production",
    surface,
  };

  const requestHeaders = new Headers(request.headers);
  requestHeaders.set(NONCE_HEADER, cspInput.nonce);

  const response = NextResponse.next({ request: { headers: requestHeaders } });
  response.headers.set("Content-Security-Policy", buildCsp(cspInput));
  response.headers.set("Content-Security-Policy-Report-Only", buildCspReportOnly(cspInput));
  return response;
}

export const config = {
  // 정적 자산은 제외하되 그 외 전 경로에 nonce가 실려야 한다 — 한 경로라도 빠지면
  // 그 페이지의 하이드레이션이 통째로 막힌다(R-3).
  matcher: ["/ops/:path*", "/((?!_next/static|_next/image|favicon\\.ico).*)"],
};
