"use client";

import type { ReactNode } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { isCurrent, primaryLinks } from "@/lib/nav-items";
import styles from "./mobile-bottom-nav.module.css";

// FE-007: 예전에는 href·라벨을 primaryLinks와 따로 정의해, 라우트가 바뀌면 두 곳을
// 고쳐야 했고 누락이 조용히 발생할 수 있었다. 이제 경로와 라벨은 레지스트리에서만 오고
// (짧은 라벨은 shortLabel), 여기에는 표현 요소인 아이콘만 남긴다.
const ICONS: Record<string, ReactNode> = {
  "/": (
    <svg viewBox="0 0 24 24" width="22" height="22" fill="none" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M4 11.5 12 4l8 7.5" />
      <path d="M6 10v9h5v-5h2v5h5v-9" />
    </svg>
  ),
  "/recommend": (
    <svg viewBox="0 0 24 24" width="22" height="22" fill="none" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M12 3.5 14.4 9l6 .8-4.4 4 1.2 5.7L12 16.6 6.8 19.5 8 13.8l-4.4-4 6-.8Z" />
    </svg>
  ),
  "/community": (
    <svg viewBox="0 0 24 24" width="22" height="22" fill="none" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M4 5h16v10H8l-4 4V5Z" />
    </svg>
  ),
  "/saved": (
    <svg viewBox="0 0 24 24" width="22" height="22" fill="none" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M6 3h12v18l-6-4-6 4V3Z" />
    </svg>
  ),
};

const BOTTOM_NAV_ITEMS = primaryLinks.map((link) => ({
  href: link.href,
  label: link.shortLabel ?? link.label,
  icon: ICONS[link.href],
}));

export function MobileBottomNav() {
  const pathname = usePathname();

  return (
    <nav className={styles.nav} aria-label="빠른 이동" data-testid="mobile-bottom-nav">
      {BOTTOM_NAV_ITEMS.map((item) => {
        const active = isCurrent(item.href, pathname);
        return (
          <Link
            key={item.href}
            href={item.href}
            className={`${styles.item} ${active ? styles.active : ""}`}
            aria-current={active ? "page" : undefined}
          >
            <span aria-hidden="true">{item.icon}</span>
            {item.label}
          </Link>
        );
      })}
    </nav>
  );
}
