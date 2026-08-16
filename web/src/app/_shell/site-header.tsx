import Link from "next/link";
import type { ReactNode } from "react";

import { ROUTES } from "@/shared/config/routes";

import { PRIMARY_NAV } from "./nav-items";
import styles from "./shell.module.css";
import { ThemeToggle } from "./theme-toggle";

/**
 * 공통 헤더. 계정 영역은 셸마다 다르므로 슬롯으로 받는다 —
 * `(public)`은 정적 로그인 링크, `(session)`은 계정 메뉴를 넣는다(§10.1).
 * 이렇게 갈라야 공개 라우트 번들에 커뮤니티 세션 코드가 들어가지 않는다.
 */
export function SiteHeader({ accountSlot }: { accountSlot?: ReactNode }) {
  return (
    <header className={styles.header}>
      <div className={`shell ${styles.headerInner}`}>
        <Link className={styles.brand} href={ROUTES.home}>
          KRAFT Lotto
        </Link>

        <nav className={styles.primaryNav} aria-label="주요 메뉴">
          {PRIMARY_NAV.flatMap((group) => group.items).map((item) => (
            <Link key={item.href} className={styles.navLink} href={item.href}>
              {item.label}
            </Link>
          ))}
        </nav>

        <div className={styles.headerActions}>
          <ThemeToggle />
          {accountSlot}
        </div>
      </div>
    </header>
  );
}
