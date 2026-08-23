// globals.css는 반드시 다른 모든 import보다 먼저 와야 한다. `@layer reset, tokens,
// base, components, utilities;` 마스터 선언이 여기 있는데, CSS Cascade Layers는
// "레이어가 처음 등장한 순서"로 우선순위를 고정한다 — 이 선언문의 소스 위치가 아니라.
// 아래에서 import하는 컴포넌트가 자기 CSS 모듈(@layer components)을 먼저 실어 오면,
// "components"가 마스터 선언보다 먼저 등록돼 base보다도 낮은 우선순위로 고정되고,
// .cta 같은 컴포넌트 스타일이 전역 `a { color }` 규칙에 조용히 진다(Phase 10 컷오버
// CI에서 실제로 겪은 접근성 회귀 — axe color-contrast).
import "./globals.css";

import type { Metadata, Viewport } from "next";
import localFont from "next/font/local";
import { headers } from "next/headers";
import type { ReactNode } from "react";

import { NONCE_HEADER } from "@/shared/config/csp";
import { publicEnv, siteVerificationMetadata } from "@/shared/config/env";
import { THEME_INIT_SCRIPT } from "@/shared/lib/theme";

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
  src: [{ path: "../../public/fonts/space-grotesk-700.woff2", weight: "700" }],
  display: "swap",
  variable: "--font-space-grotesk",
  preload: false,
});

export const metadata: Metadata = {
  metadataBase: new URL(publicEnv.baseUrl),
  title: { default: "KRAFT Lotto", template: "%s | KRAFT Lotto" },
  verification: siteVerificationMetadata,
};

/**
 * safe-area 정책: `viewportFit: "cover"`를 켜지 않는다(FE-RSP-05).
 *
 * cover를 켜면 배경이 노치/홈 인디케이터 영역까지 확장되고, 그 결과 header/footer/
 * dialog/drawer 등 좌우로 맞닿는 모든 요소에 `env(safe-area-inset-left/right/top)`을
 * 새로 추가해야 한다 — 지금은 이 프로젝트에 그런 edge-to-edge 배경이나 좌우 컷아웃과
 * 맞닿는 UI가 없다(shell은 항상 --space-4 이상의 좌우 gutter를 둔다). 즉 이 앱은
 * `viewportFit: "cover"` 없이도 기본 컨테이닝 블록이 이미 safe area 밖에 머무른다.
 *
 * 반면 하단 고정 UI(tabBar/StickyMobileAd/toast/drawer)는 `position: fixed`로 화면
 * 맨 아래에 붙으므로 홈 인디케이터에 가려질 수 있다 — 그래서 `env(safe-area-inset-bottom)`
 * 만 명시적으로 방어한다(shell.module.css:78,85,98,146,154, ad-unit.module.css:49,
 * overlay.module.css:46). 이것이 "하단 전용" 정책이다.
 *
 * edge-to-edge를 채택할 이유(디자인 변경)가 생기면 `viewportFit: "cover"`를 켜고
 * 위 목록의 각 fixed 요소에 좌우/상단 inset을 함께 추가해야 한다 — 하나만 켜면
 * 배경은 늘어나는데 UI는 노치를 방어하지 못하는 상태가 된다.
 */
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
 *
 * FE-SEC-01(docs/improvement.md): `WebVitalsReporter`·`StickyMobileAd`도 더 이상 여기
 * 없다. 이전에는 루트 `<body>`가 두 컴포넌트를 모든 라우트 그룹에 렌더해, `/ops` 운영
 * 콘솔이 (ops)/layout.tsx로 공개 내비를 빼도 광고 SDK·공개 RUM은 여전히 로드됐고
 * ops 응답 CSP도 광고 네트워크 origin을 항상 허용했다. 이제 두 컴포넌트는
 * `(public)/layout.tsx`·`(session)/layout.tsx`에만 있다 — `(ops)/layout.tsx`는
 * 렌더하지 않는다.
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
        {children}
      </body>
    </html>
  );
}
