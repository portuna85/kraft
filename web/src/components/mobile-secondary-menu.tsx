"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { IconButton } from "@/ui/primitives/icon-button";
import { Drawer } from "@/ui/primitives/drawer";
import { dataLinks, isCurrent } from "@/lib/nav-items";
import { AccountMenu } from "@/components/community/account-menu";
import { ThemeToggle } from "@/components/theme-toggle";
import { BP } from "@/lib/breakpoints";
import styles from "./mobile-secondary-menu.module.css";

const STATUS_LINK = { href: "/status", label: "서비스 상태" };

function HamburgerIcon() {
  return (
    <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
      <path d="M4 6h16M4 12h16M4 18h16" />
    </svg>
  );
}

export function MobileSecondaryMenu() {
  const pathname = usePathname();
  const [open, setOpen] = useState(false);
  const toggleRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    // 열어둔 채로 데스크톱 폭까지 리사이즈하면 트리거 버튼은 CSS로 숨겨지지만(header.module.css
    // .mobileOnly) Drawer 자체는 open 상태와 무관하게 계속 화면을 덮는다 — 예전 nav-links.tsx의
    // 동일한 리사이즈 자동 닫힘 로직을 그대로 옮긴다.
    const mq = window.matchMedia(`(min-width: ${BP.desktop}px)`);
    const onChange = (e: MediaQueryListEvent) => {
      if (e.matches) setOpen(false);
    };
    mq.addEventListener("change", onChange);
    return () => mq.removeEventListener("change", onChange);
  }, []);

  return (
    <>
      <IconButton
        ref={toggleRef}
        aria-label={open ? "메뉴 닫기" : "메뉴 열기"}
        variant="quiet"
        icon={<HamburgerIcon />}
        onClick={() => setOpen((value) => !value)}
      />
      <Drawer
        open={open}
        onClose={() => setOpen(false)}
        restoreFocusRef={toggleRef}
        side="right"
        titleId="mobile-secondary-menu-title"
        title="더 보기"
      >
        <nav className={styles.list} aria-label="보조 메뉴">
          <p className={styles.heading}>데이터</p>
          {dataLinks.map((link) => (
            <Link
              key={link.href}
              href={link.href}
              onClick={() => setOpen(false)}
              aria-current={isCurrent(link.href, pathname) ? "page" : undefined}
            >
              {link.label}
            </Link>
          ))}

          <p className={styles.heading}>안내</p>
          <Link
            href={STATUS_LINK.href}
            onClick={() => setOpen(false)}
            aria-current={isCurrent(STATUS_LINK.href, pathname) ? "page" : undefined}
          >
            {STATUS_LINK.label}
          </Link>
        </nav>
        <div className={styles.footer}>
          <AccountMenu />
          <ThemeToggle />
        </div>
      </Drawer>
    </>
  );
}
