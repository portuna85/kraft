import type { Metadata, Viewport } from "next";
import { headers } from "next/headers";
import localFont from "next/font/local";
import { Footer } from "@/components/footer";
import { Header } from "@/components/header";
import { CommunitySessionProvider } from "@/components/community/community-session-provider";
import { StickyMobileAd } from "@/components/ad-unit";
import { WebVitalsReporter } from "@/components/web-vitals-reporter";
import { JsonLdWebSite } from "@/components/json-ld";
import { getPublicBaseUrl } from "@/lib/api";
import { THEME_INIT_SCRIPT } from "@/lib/csp-inline-scripts";
import "./globals.css";

// F1: next/font/google은 빌드마다 fonts.gstatic.com 네트워크가 필요해 production build의
// 단일 실패 지점이었다. google/fonts 공식 저장소(OFL 라이선스)에서 받은 폰트를
// scripts/fetch-fonts.mjs로 미리 weight별 static instance woff2로 만들어 커밋해두고
// next/font/local로 자체 호스팅한다. 갱신 방법은 scripts/fetch-fonts.mjs 상단 주석 참고.
const notoSansKR = localFont({
  src: [
    { path: "../../public/fonts/noto-sans-kr-400.woff2", weight: "400" },
    { path: "../../public/fonts/noto-sans-kr-700.woff2", weight: "700" },
  ],
  display: "swap",
  variable: "--font-sans",
});

// noto-serif-kr-700은 히어로 제목(.page-title/.result-title)에만 쓰이는 non-critical
// 자산이라 preload에서 제외한다 — 크리티컬 렌더 패스를 본문 폰트(notoSansKR)에 집중시킨다.
const notoSerifKR = localFont({
  src: [{ path: "../../public/fonts/noto-serif-kr-700.woff2", weight: "700" }],
  display: "swap",
  variable: "--font-display",
  preload: false,
});

const spaceGrotesk = localFont({
  src: [
    { path: "../../public/fonts/space-grotesk-500.woff2", weight: "500" },
    { path: "../../public/fonts/space-grotesk-700.woff2", weight: "700" },
  ],
  display: "swap",
  variable: "--font-accent",
});

const baseUrl = getPublicBaseUrl();

export const viewport: Viewport = {
  // R-23: 라이트/다크 OS 스킴에 근사 대응 — 사이트 테마는 토글식이라 완전 일치는
  // 불가능하지만(테마 초기화는 JS로 body에 반영), media 배열이 최선이다.
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#c94f24" },
    { media: "(prefers-color-scheme: dark)", color: "#1c1813" },
  ],
  width: "device-width",
  initialScale: 1,
  viewportFit: "cover",
};

export const metadata: Metadata = {
  metadataBase: new URL(baseUrl),
  title: {
    default: "KRAFT Lotto | 로또 6/45 결과와 번호 추천",
    template: "%s | KRAFT Lotto",
  },
  description: "로또 6/45 최신 당첨 번호 조회, 회차 검색, 통계, 번호 추천 기능을 제공합니다.",
  alternates: {
    canonical: "/",
  },
  openGraph: {
    title: "KRAFT Lotto | 로또 6/45 결과와 번호 추천",
    description: "최신 로또 당첨 번호 조회, 출현 통계, 패턴 분석, 번호 추천 기능을 제공합니다.",
    url: baseUrl,
    siteName: "KRAFT Lotto",
    locale: "ko_KR",
    type: "website",
  },
  twitter: {
    card: "summary_large_image",
    title: "KRAFT Lotto | 로또 6/45 결과와 번호 추천",
    description: "최신 로또 당첨 번호 조회, 출현 통계, 패턴 분석, 번호 추천 기능을 제공합니다.",
  },
  verification: {
    google: process.env.NEXT_PUBLIC_GOOGLE_SITE_VERIFICATION,
    other: process.env.NEXT_PUBLIC_NAVER_SITE_VERIFICATION
      ? { "naver-site-verification": process.env.NEXT_PUBLIC_NAVER_SITE_VERIFICATION }
      : undefined,
  },
};

export default async function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  const nonce = (await headers()).get("x-nonce") ?? undefined;

  return (
    // THEME_INIT_SCRIPT가 hydration 전에 저장된 테마를 data-theme에 반영한다.
    // 서버는 localStorage를 읽을 수 없어 이 속성 차이가 의도적으로 발생한다.
    <html
      lang="ko"
      className={`${notoSansKR.variable} ${notoSerifKR.variable} ${spaceGrotesk.variable}`}
      suppressHydrationWarning
    >
      <body>
        <script
          nonce={nonce}
          suppressHydrationWarning
          dangerouslySetInnerHTML={{ __html: THEME_INIT_SCRIPT }}
        />
        <JsonLdWebSite baseUrl={baseUrl} nonce={nonce} />
        <WebVitalsReporter />
        <a href="#main-content" className="skip-nav">본문으로 건너뛰기</a>
        <CommunitySessionProvider>
          <Header />
          <main id="main-content" className="page">
            <div className="shell">{children}</div>
          </main>
          <Footer />
          <StickyMobileAd unit={process.env.NEXT_PUBLIC_KAKAO_ADFIT_UNIT_STICKY ?? ""} />
        </CommunitySessionProvider>
      </body>
    </html>
  );
}
