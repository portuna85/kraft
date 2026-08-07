import type { NextConfig } from "next";

// 산출물 형태와 URL 계약은 인프라 제약이라 재작성 대상이 아니다(improvement_fe.md §8.1,
// §24.2): standalone 산출물 + 컨테이너 포트 3000, 공개 라우트 URL 15개, permanent
// 리다이렉트 2개, /ops-api rewrite는 Caddy·백엔드와 이미 맞춰져 있다.
//
// NEXT_DIST_DIR: 광고 env를 baked-in한 별도 빌드처럼 산출물을 분리해야 하는 e2e 트랙이
// 기본 .next를 덮어쓰지 않게 한다. 미지정 시 기본 동작과 동일하다.
const distDir = process.env.NEXT_DIST_DIR;

const nextConfig: NextConfig = {
  output: "standalone",
  ...(distDir ? { distDir } : {}),
  poweredByHeader: false,
  allowedDevOrigins: ["127.0.0.1"],
  async redirects() {
    return [
      { source: "/data-source", destination: "/info/data-source", permanent: true },
      { source: "/latest", destination: "/", permanent: true },
    ];
  },
  async rewrites() {
    const backendUrl = process.env.KRAFT_BACKEND_INTERNAL_URL ?? "http://backend:8080";

    return [{ source: "/ops-api/:path*", destination: `${backendUrl}/ops/:path*` }];
  },
};

export default nextConfig;
