import type { NextConfig } from "next";

// 광고 오버레이(NEXT_PUBLIC_KAKAO_ADFIT_UNIT_*)가 CTA를 안 가리는지 검증하려면
// 그 env를 baked-in한 별도 빌드가 필요한데, 기본 산출물(.next)을 그대로 쓰면
// 콘텐츠 트랙 등 다른 e2e 트랙과 산출물을 덮어써버린다. NEXT_DIST_DIR을 지정했을 때만
// distDir을 바꾸고, 미지정 시(로컬 기본 빌드·Docker 빌드 전부) 기존 동작과 100% 동일하다.
const distDir = process.env.NEXT_DIST_DIR;

const nextConfig: NextConfig = {
  output: "standalone",
  ...(distDir ? { distDir } : {}),
  poweredByHeader: false,
  allowedDevOrigins: ["127.0.0.1"],
  async redirects() {
    return [
      {
        source: "/data-source",
        destination: "/info/data-source",
        permanent: true,
      },
      {
        source: "/latest",
        destination: "/",
        permanent: true,
      },
    ];
  },
  async rewrites() {
    const backendUrl = process.env.KRAFT_BACKEND_INTERNAL_URL ?? "http://backend:8080";

    return [
      {
        source: "/ops-api/:path*",
        destination: `${backendUrl}/ops/:path*`,
      },
    ];
  },
};

export default nextConfig;
