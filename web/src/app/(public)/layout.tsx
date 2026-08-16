import type { ReactNode } from "react";

import { LoginErrorBanner } from "../_shell/login-error-banner";
import { PublicAccountSlot } from "../_shell/public-account-slot";
import { ReturnToRedirect } from "../_shell/return-to-redirect";
import styles from "../_shell/shell.module.css";
import { SiteFooter } from "../_shell/site-footer";
import { SiteHeader } from "../_shell/site-header";
import { TabBar } from "../_shell/tab-bar";

/**
 * 공개 셸
 *
 * **세션 프로바이더가 없다.** 현행은 계정 메뉴가 전 페이지에 있어 프로바이더를 루트로
 * 올릴 수밖에 없었고, 그 결과 `/`·`/stats`·`/info/*` 같은 완전 공개 라우트도 커뮤니티
 * 세션 코드를 클라이언트 번들에 포함했다(§6.3 M-4). 계정 영역을 정적 로그인 링크로
 * 대체하면 그 비용이 사라진다.
 *
 * I-03: 계정 영역은 이제 `PublicAccountSlot`(요청 쿠키의 비식별 boolean만 읽는 서버
 * 컴포넌트)이 맡는다 — 세션 API는 여전히 부르지 않는다.
 */
export default function PublicLayout({ children }: { children: ReactNode }) {
  return (
    <>
      <ReturnToRedirect />
      <SiteHeader accountSlot={<PublicAccountSlot />} />
      <LoginErrorBanner />
      <main id="main" className={`shell ${styles.main}`}>
        {children}
      </main>
      <SiteFooter />
      <TabBar />
    </>
  );
}
