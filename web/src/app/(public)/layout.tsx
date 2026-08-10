import type { ReactNode } from "react";

import { LoginPopover } from "../_shell/login-popover";
import styles from "../_shell/shell.module.css";
import { SiteFooter } from "../_shell/site-footer";
import { SiteHeader } from "../_shell/site-header";
import { TabBar } from "../_shell/tab-bar";

/**
 * 공개 셸 — improvement_fe.md §10.1
 *
 * **세션 프로바이더가 없다.** 현행은 계정 메뉴가 전 페이지에 있어 프로바이더를 루트로
 * 올릴 수밖에 없었고, 그 결과 `/`·`/stats`·`/info/*` 같은 완전 공개 라우트도 커뮤니티
 * 세션 코드를 클라이언트 번들에 포함했다(§6.3 M-4). 계정 영역을 정적 로그인 링크로
 * 대체하면 그 비용이 사라진다.
 */
export default function PublicLayout({ children }: { children: ReactNode }) {
  return (
    <>
      <SiteHeader accountSlot={<LoginPopover />} />
      <main id="main" className={`shell ${styles.main}`}>
        {children}
      </main>
      <SiteFooter />
      <TabBar />
    </>
  );
}
