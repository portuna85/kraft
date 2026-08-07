"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

import { TAB_BAR_ITEMS } from "./nav-items";
import styles from "./shell.module.css";

/**
 * 모바일 하단 탭. 현재 위치 표시에 pathname이 필요해 클라이언트 컴포넌트다 —
 * 셸에서 유일하게 JS를 쓰는 부분이라 여기 하나로 격리한다(§19.2 P-8: "use client"를
 * 잎에 가깝게).
 */
export function TabBar() {
  const pathname = usePathname();

  return (
    <nav className={styles.tabBar} aria-label="바로가기">
      {TAB_BAR_ITEMS.map((item) => {
        const isCurrent =
          item.href === "/" ? pathname === "/" : (pathname?.startsWith(item.href) ?? false);
        return (
          <Link
            key={item.href}
            className={styles.tabLink}
            href={item.href}
            aria-current={isCurrent ? "page" : undefined}
          >
            {item.label}
          </Link>
        );
      })}
    </nav>
  );
}
