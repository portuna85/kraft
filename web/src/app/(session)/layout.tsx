import type { ReactNode } from "react";

import { SessionProvider } from "@/features/identity-session/session-provider";

import { AccountControl } from "../_shell/account-control";
import { ReturnToRedirect } from "../_shell/return-to-redirect";
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
 * BlockedUsersProvider는 커뮤니티 구현과 함께 이 안으로 들어온다(§14.3).
 */
export default function SessionLayout({ children }: { children: ReactNode }) {
  return (
    <SessionProvider>
      <ReturnToRedirect />
      <SiteHeader accountSlot={<AccountControl />} />
      <main id="main" className={`shell ${styles.main}`}>
        {children}
      </main>
      <SiteFooter />
      <TabBar />
    </SessionProvider>
  );
}
