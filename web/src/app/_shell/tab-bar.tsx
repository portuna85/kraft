"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

import { currentNavHref, TAB_BAR_ITEMS } from "./nav-items";
import styles from "./shell.module.css";

/**
 * 모바일 하단 탭. 현재 위치 표시에 pathname이 필요해 클라이언트 컴포넌트다 —
 * 셸에서 유일하게 JS를 쓰는 부분이라 여기 하나로 격리한다(§19.2 P-8: "use client"를
 * 잎에 가깝게).
 */
export function TabBar() {
  const pathname = usePathname();
  // RSP-23/24(docs/improvement.md): 예전에는 인디케이터가 `findIndex`로 첫 매치
  // 하나를 고르고, 각 링크는 `isRouteCurrent`를 독립적으로 다시 계산했다. 두 탭이
  // 동시에 걸리면 `aria-current`는 둘인데 캡슐은 하나만 움직여, 시각 표시와 보조
  // 기술이 서로 다른 답을 냈다. 이제 둘 다 이 하나의 결과에서 나온다.
  const currentHref = currentNavHref(TAB_BAR_ITEMS, pathname);
  const activeIndex = TAB_BAR_ITEMS.findIndex((item) => item.href === currentHref);

  return (
    <nav
      className={styles.tabBar}
      aria-label="바로가기"
      data-active-index={activeIndex >= 0 ? activeIndex : undefined}
    >
      {/* 활성 탭 뒤로 슬라이드하는 캡슐 배경. 탭 개수가 고정(5개, nav-items.ts)이라
          위치는 CSS 속성 선택자로 미리 정의한다 — 위치 계산을 위해 인라인 style을
          쓰면 style-src CSP에서 unsafe-inline을 뺄 수 없다(frequency-bar 주석과 동일 이유). */}
      <span className={styles.indicator} aria-hidden="true" />

      {TAB_BAR_ITEMS.map((item) => {
        const isCurrent = item.href === currentHref;
        return (
          <Link
            key={item.href}
            className={styles.tabLink}
            href={item.href}
            aria-current={isCurrent ? "page" : undefined}
          >
            <span className={styles.tabLabel}>{item.label}</span>
          </Link>
        );
      })}
    </nav>
  );
}
