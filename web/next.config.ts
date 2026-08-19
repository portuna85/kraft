import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

import type { NextConfig } from "next";

// KF-36(docs/improvement.md): RUM이 release를 항상 빈 문자열로 보내고 있었다 —
// npm_package_version은 npm으로 직접 실행할 때만 보장되므로(Docker 런타임은
// node로 직접 실행) 빌드 타임에 package.json을 읽어 고정 주입한다.
const webDir = path.dirname(fileURLToPath(import.meta.url));
const appVersion = (
  JSON.parse(readFileSync(path.join(webDir, "package.json"), "utf-8")) as { version: string }
).version;

// 산출물 형태와 URL 계약은 인프라 제약이라 재작성 대상이 아니다: standalone 산출물 +
// 컨테이너 포트 3000, 공개 라우트 URL 15개, permanent 리다이렉트 2개, /ops-api rewrite는
// Caddy·백엔드와 이미 맞춰져 있다.
//
// NEXT_DIST_DIR: 광고 env를 baked-in한 별도 빌드처럼 산출물을 분리해야 하는 e2e 트랙이
// 기본 .next를 덮어쓰지 않게 한다. 미지정 시 기본 동작과 동일하다.
const distDir = process.env.NEXT_DIST_DIR;

const nextConfig: NextConfig = {
  output: "standalone",
  ...(distDir ? { distDir } : {}),
  poweredByHeader: false,
  env: { NEXT_PUBLIC_APP_VERSION: appVersion },
  allowedDevOrigins: ["127.0.0.1"],
  async redirects() {
    return [
      { source: "/data-source", destination: "/info/data-source", permanent: true },
      { source: "/latest", destination: "/", permanent: true },
    ];
  },
  async rewrites() {
    // REF-05: 프로덕션에서는 Caddy가 /api/v1/*·/ops-api/*를 Next를 거치지 않고 backend로
    // 직접 보낸다(caddy/Caddyfile `handle`) — 이 rewrite는 그 앞단을 통과하지 못하므로
    // 실제로는 절대 실행되지 않는다. Caddy 없이 standalone 서버만 띄우는 E2E 트랙(모든
    // playwright.*.config.ts가 scripts/start-standalone.mjs로 이렇게 띄운다)에서만 브라우저
    // 쪽 요청(browserQuery/browserMutate)이 갈 곳이 없어 필요하다 — 그 트랙들의 CI 빌드
    // 스텝에서만 NEXT_ENABLE_STANDALONE_REWRITES=true를 준다. 프로덕션 빌드에는 절대 켜지
    // 않아 죽은 rewrite 규칙이 산출물에 남지 않는다.
    if (process.env.NEXT_ENABLE_STANDALONE_REWRITES !== "true") return [];

    const backendUrl = process.env.KRAFT_BACKEND_INTERNAL_URL ?? "http://backend:8080";
    return [
      { source: "/ops-api/:path*", destination: `${backendUrl}/ops/:path*` },
      { source: "/api/v1/:path*", destination: `${backendUrl}/api/v1/:path*` },
    ];
  },
};

export default nextConfig;
