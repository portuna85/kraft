import type { ReactNode } from "react";

import { ClaimMergeToast } from "@/features/identity-session/claim-merge-toast";
import { SessionProvider } from "@/features/identity-session/session-provider";
import { publicEnv } from "@/shared/config/env";
import { StickyMobileAd } from "@/shared/ui/sticky-mobile-ad";
import { ToastProvider } from "@/shared/ui/toast";
import { WebVitalsReporter } from "@/shared/ui/web-vitals-reporter";

import { AccountControl } from "../_shell/account-control";
import { ReturnToRedirect } from "../_shell/return-to-redirect";
import styles from "../_shell/shell.module.css";
import { SiteFooter } from "../_shell/site-footer";
import { SiteHeader } from "../_shell/site-header";
import { TabBar } from "../_shell/tab-bar";

/**
 * 세션 셸
 *
 * 세션이 필요한 라우트(`/community`·`/saved`·`/recommend`)만 이 셸 아래 둔다. 세션
 * 스코프를 라우트 그룹으로 표현하면 불변식 I-4("스코프 밖에서는 세션 API를 호출하지
 * 않는다")가 런타임 조건문이 아니라 구조로 지켜진다.
 *
 * FE-SEC-01(docs/improvement.md): `WebVitalsReporter`·`StickyMobileAd`가 루트 레이아웃
 * 대신 이 셸과 `(public)` 셸에만 있다 — `(ops)` 셸은 광고·공개 RUM과 완전히 분리돼야 한다.
 */
export default function SessionLayout({ children }: { children: ReactNode }) {
  return (
    <SessionProvider>
      <ToastProvider>
        <WebVitalsReporter />
        <ReturnToRedirect />
        <ClaimMergeToast />
        <SiteHeader accountSlot={<AccountControl />} />
        <main id="main" className={`shell ${styles.main}`}>
          {children}
        </main>
        <SiteFooter />
        <TabBar />
        <StickyMobileAd unit={publicEnv.kakaoAdfitUnitSticky} />
      </ToastProvider>
    </SessionProvider>
  );
}
