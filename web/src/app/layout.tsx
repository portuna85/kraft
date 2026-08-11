import type { Metadata, Viewport } from "next";
import localFont from "next/font/local";
import { headers } from "next/headers";
import type { ReactNode } from "react";

import { NONCE_HEADER } from "@/shared/config/csp";
import { publicEnv, siteVerificationMetadata } from "@/shared/config/env";
import { THEME_INIT_SCRIPT } from "@/shared/lib/theme";
import { StickyMobileAd } from "@/shared/ui/ad-unit";
import { WebVitalsReporter } from "@/shared/ui/web-vitals-reporter";

import "./globals.css";

/*
 * 폰트는 자체 호스팅한다. next/font/google은 빌드마다 fonts.gstatic.com 네트워크를 타서
 * 프로덕션 빌드의 단일 실패 지점이 된다.
 *
 * 전에는 두 폰트 모두 preload를 껐다 — 완성형 한글 11,172자를 전부 담은 전역 프리로드
 * 1.29 MiB가 초기 네트워크 경쟁을 지배해 `/`의 LCP가 3,087ms로 예산(2,500ms)을
 * 넘겼다(레거시 H-3). 지금은 scripts/fetch-fonts.mjs가 한글 서브셋을 KS X 1001
 * 현대한글 2,350자로 좁혀 본문 폰트(noto-sans-kr) 합계가 536 KB로 줄었다(§19.2 P-5) —
 * 그래서 본문 폰트만 preload를 켠다. Space Grotesk는 이미 30 KB대로 작아 preload 여부가
 * LCP에 미치는 영향이 없어 그대로 끈 채로 둔다.
 */
const notoSansKr = localFont({
  src: [
    { path: "../../public/fonts/noto-sans-kr-400.woff2", weight: "400" },
    { path: "../../public/fonts/noto-sans-kr-700.woff2", weight: "700" },
  ],
  display: "swap",
  variable: "--font-noto-sans-kr",
  preload: true,
});

const spaceGrotesk = localFont({
  src: [
    { path: "../../public/fonts/space-grotesk-500.woff2", weight: "500" },
    { path: "../../public/fonts/space-grotesk-700.woff2", weight: "700" },
  ],
  display: "swap",
  variable: "--font-space-grotesk",
  preload: false,
});

export const metadata: Metadata = {
  metadataBase: new URL(publicEnv.baseUrl),
  title: { default: "KRAFT Lotto", template: "%s | KRAFT Lotto" },
  verification: siteVerificationMetadata,
};

export const viewport: Viewport = {
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#f3f7ff" },
    { media: "(prefers-color-scheme: dark)", color: "#050816" },
  ],
};

/**
 * 루트 레이아웃은 html/body·폰트 변수·테마 부트스트랩·skip-nav만 소유한다.
 * 헤더·푸터·탭바는 라우트 그룹별 셸이 각자 갖는다 — 공개 라우트가 커뮤니티 세션 코드를
 * 번들에 포함하지 않게 하려면 셸이 갈라져 있어야 한다(§6.3 M-4, §10.1).
 */
export default async function RootLayout({ children }: { children: ReactNode }) {
  // proxy가 매 요청 발급한 nonce. 없으면 인라인 스크립트가 CSP에 막히므로,
  // 조용히 넘어가지 않고 빈 문자열 대신 undefined로 두어 개발 중에 눈에 띄게 한다.
  const nonce = (await headers()).get(NONCE_HEADER) ?? undefined;

  return (
    <html lang="ko" className={`${notoSansKr.variable} ${spaceGrotesk.variable}`}>
      <head>
        {/* 하이드레이션 전에 data-theme을 확정해 첫 페인트 번쩍임을 없앤다. */}
        <script nonce={nonce} dangerouslySetInnerHTML={{ __html: THEME_INIT_SCRIPT }} />
      </head>
      <body>
        <a className="skip-nav" href="#main">
          본문으로 건너뛰기
        </a>
        <WebVitalsReporter />
        {children}
        <StickyMobileAd unit={process.env.NEXT_PUBLIC_KAKAO_ADFIT_UNIT_STICKY ?? ""} />
      </body>
    </html>
  );
}
