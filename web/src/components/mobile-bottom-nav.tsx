"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { isCurrent } from "@/lib/nav-items";
import styles from "./mobile-bottom-nav.module.css";

// primaryLinks(nav-items.ts)와 href는 같지만, 하단 탭 바는 공간이 좁아 더 짧은
// 라벨을 쓴다(목표 IA §3.2: "홈/추천/커뮤니티/보관함").
const BOTTOM_NAV_ITEMS = [
  {
    href: "/",
    label: "홈",
    icon: (
      <svg viewBox="0 0 24 24" width="22" height="22" fill="none" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M4 11.5 12 4l8 7.5" />
        <path d="M6 10v9h5v-5h2v5h5v-9" />
      </svg>
    ),
  },
  {
    href: "/recommend",
    label: "추천",
    icon: (
      <svg viewBox="0 0 24 24" width="22" height="22" fill="none" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M12 3.5 14.4 9l6 .8-4.4 4 1.2 5.7L12 16.6 6.8 19.5 8 13.8l-4.4-4 6-.8Z" />
      </svg>
    ),
  },
  {
    href: "/community",
    label: "커뮤니티",
    icon: (
      <svg viewBox="0 0 24 24" width="22" height="22" fill="none" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M4 5h16v10H8l-4 4V5Z" />
      </svg>
    ),
  },
  {
    href: "/saved",
    label: "보관함",
    icon: (
      <svg viewBox="0 0 24 24" width="22" height="22" fill="none" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M6 3h12v18l-6-4-6 4V3Z" />
      </svg>
    ),
  },
] as const;

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
