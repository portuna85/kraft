import type { ReactNode } from "react";

import { LoginLinks } from "../_shell/login-links";
import styles from "../_shell/shell.module.css";
import { SiteFooter } from "../_shell/site-footer";
import { SiteHeader } from "../_shell/site-header";
import { TabBar } from "../_shell/tab-bar";

/**
 * 세션 셸 — improvement_fe.md §10.1, §14.3
 *
 * 세션이 필요한 라우트(`/community`·`/saved`·`/recommend`)만 이 셸 아래 둔다. 세션
 * 스코프를 라우트 그룹으로 표현하면 불변식 I-4("스코프 밖에서는 세션 API를 호출하지
 * 않는다")가 런타임 조건문이 아니라 구조로 지켜진다.
 *
 * SessionProvider와 BlockedUsersProvider는 Phase 4에서 여기에 들어온다 — 세션
 * 상태머신은 이 재작성의 최고 위험군(§25.2)이라 셸보다 먼저 만들지 않는다.
 */
export default function SessionLayout({ children }: { children: ReactNode }) {
  return (
    <>
      <SiteHeader accountSlot={<LoginLinks />} />
      <main id="main" className={`shell ${styles.main}`}>
        {children}
      </main>
      <SiteFooter />
      <TabBar />
    </>
  );
}
