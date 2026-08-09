import type { Metadata, Viewport } from "next";
import { headers } from "next/headers";
import localFont from "next/font/local";
import { Footer } from "@/components/footer";
import { Header } from "@/components/header";
import { MobileBottomNav } from "@/components/mobile-bottom-nav";
import { CommunitySessionProvider } from "@/lib/community-session-provider";
import { BlockedUsersProvider } from "@/features/community/blocked-users-context";
import { ReturnToRedirect } from "@/components/return-to-redirect";
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
//
// H-3: 두 폰트 모두 preload를 끈다 — 커밋된 lighthouse-results.json이 이미 LCP 예산
// 초과(`/` 3,087ms > 2,500ms 등)를 보여줬고, 전역 프리로드 1.29 MiB가 초기 네트워크
// 경쟁을 지배하는 것이 원인으로 지목됐다. FOUT를 감수하고 크리티컬 경로 전송량을 줄인다.
//
// P-CLS-1: Noto Sans KR만 swap 대신 optional을 쓴다 — Lighthouse layout-shifts
// 진단으로 실측한 결과, 홈의 RecommendEntryCard가 노토 산스 KR 700(671KB)이
// 폴백 폰트를 교체하는 순간 줄바꿈이 바뀌며 CLS 0.086~0.175(예산 0.051 초과)를
// 유발했다 — CJK 폰트는 폴백과 글리프 폭 차이가 커서 swap 자체가 셔프트를
// 만든다. preload로 671KB를 다시 얹으면 H-3가 고친 LCP 회귀가 재현되므로
// (실측 확인: preload 시 위와 동일한 규모), optional로 바꿔 "이번 방문에서 폰트가
// 늦게 오면 폴백을 계속 쓰고 교체하지 않는다"를 택했다 — 첫 방문자는 폴백 폰트를
// 볼 수 있지만(브랜드 일관성 트레이드오프), 캐시된 재방문에서는 그대로 노토
// 산스 KR로 렌더된다. Space Grotesk(브랜드 accent, 14KB대)는 shift 기여가
// 0.00014로 무시할 수준이라 그대로 swap을 유지한다.
const notoSansKR = localFont({
  src: [
    { path: "../../public/fonts/noto-sans-kr-400.woff2", weight: "400" },
    { path: "../../public/fonts/noto-sans-kr-700.woff2", weight: "700" },
  ],
  display: "optional",
  variable: "--font-sans",
  preload: false,
});

const spaceGrotesk = localFont({
  src: [
    { path: "../../public/fonts/space-grotesk-500.woff2", weight: "500" },
    { path: "../../public/fonts/space-grotesk-700.woff2", weight: "700" },
  ],
  display: "swap",
  variable: "--font-accent",
  preload: false,
});

const baseUrl = getPublicBaseUrl();

export const viewport: Viewport = {
  // R-23: 라이트/다크 OS 스킴에 근사 대응 — 사이트 테마는 토글식이라 완전 일치는
  // 불가능하지만(테마 초기화는 JS로 body에 반영), media 배열이 최선이다.
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#f3f7ff" },
    { media: "(prefers-color-scheme: dark)", color: "#050816" },
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
      className={`${notoSansKR.variable} ${spaceGrotesk.variable}`}
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
        <ReturnToRedirect />
        <CommunitySessionProvider>
          <BlockedUsersProvider>
            <Header />
            <main id="main-content" className="page">
              <div className="shell">{children}</div>
            </main>
            <Footer />
            <MobileBottomNav />
            <StickyMobileAd unit={process.env.NEXT_PUBLIC_KAKAO_ADFIT_UNIT_STICKY ?? ""} />
          </BlockedUsersProvider>
        </CommunitySessionProvider>
      </body>
    </html>
  );
}
