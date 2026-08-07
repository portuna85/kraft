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
  // 별 아이콘은 "보관함"의 북마크 아이콘과 마찬가지로 관례상 저장·즐겨찾기를
  // 의미해 두 탭이 시각적으로 충돌했다 — 조합을 만든다는 의미가 분명한 주사위로 교체.
  "/recommend": (
    <svg viewBox="0 0 24 24" width="22" height="22" fill="none" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <rect x="4" y="4" width="16" height="16" rx="3" />
      <circle cx="8.5" cy="8.5" r="1.3" fill="currentColor" stroke="none" />
      <circle cx="15.5" cy="8.5" r="1.3" fill="currentColor" stroke="none" />
      <circle cx="12" cy="12" r="1.3" fill="currentColor" stroke="none" />
      <circle cx="8.5" cy="15.5" r="1.3" fill="currentColor" stroke="none" />
      <circle cx="15.5" cy="15.5" r="1.3" fill="currentColor" stroke="none" />
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
