"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

import { currentNavHref, PRIMARY_NAV } from "./nav-items";
import styles from "./shell.module.css";

/**
 * KF-25②(docs/improvement.md): 데스크톱 내비게이션이 현재 라우트를 노출하지
 * 않았다 — 모바일 TabBar는 `aria-current`·활성 스타일을 갖췄는데 뷰포트별로
 * 동작이 달랐다. TabBar와 정확히 같은 패턴("use client"를 잎에 가깝게,
 * §19.2 P-8) — `SiteHeader` 자체는 서버 컴포넌트로 남기고 이 리프만 pathname을
 * 읽는다.
 */
export function PrimaryNav() {
  const pathname = usePathname();
  const allItems = PRIMARY_NAV.flatMap((group) => group.items);
  const currentHref = currentNavHref(allItems, pathname);

  return (
    <nav className={styles.primaryNav} aria-label="주요 메뉴">
      {PRIMARY_NAV.flatMap((group) => group.items).map((item) => (
        <Link
          key={item.href}
          className={styles.navLink}
          href={item.href}
          aria-current={item.href === currentHref ? "page" : undefined}
        >
          {item.label}
        </Link>
      ))}
    </nav>
  );
}
