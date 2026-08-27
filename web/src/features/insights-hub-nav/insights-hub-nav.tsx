"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

import { ROUTES } from "@/shared/config/routes";

import styles from "./insights-hub-nav.module.css";

const ITEMS = [
  { href: ROUTES.data, label: "개요" },
  { href: ROUTES.frequency, label: "출현 통계" },
  { href: ROUTES.stats, label: "패턴 통계" },
  { href: ROUTES.companion, label: "동반 출현" },
  { href: ROUTES.info("data-source"), label: "데이터 출처" },
] as const;

/**
 * 인사이트 허브 탭
 *
 * kraft-redesign-plan.md §4 "Insights sub-navigation"은 Overview·Number Frequency·
 * Winning Patterns·Companion Numbers·Data Source & Methodology 다섯 항목을
 * 요구하고, §6 "Insights"는 이를 "하나의 허브"로 통합하라고 한다. 네 화면
 * (`/data`·`/frequency`·`/stats`·`/companion`)은 서로 다른 백엔드 엔드포인트를
 * 조회하고 각자 다른 필터(기간·번호)를 쓴다 — 목록 자체를 하나로 합치거나 공유
 * 필터를 두려면 백엔드가 네 통계를 아우르는 새 계약을 줘야 하고, 그건 프론트
 * 단독 세션 범위를 벗어난다. `SavedHistoryNav`(kraft-redesign-plan.md §6 "Saved
 * and History")와 같은 이유로, 대신 다섯 화면이 **같은 자리에서 서로를 오가는
 * 탭처럼** 보이게 한다 — `/frequency`의 기간 필터 pill과 같은 시각 언어
 * (`pillActive`)를 그대로 재사용한다.
 *
 * `PrimaryNav`/`SavedHistoryNav`와 같은 이유로 클라이언트 컴포넌트다 — 현재
 * 라우트 판정에 `usePathname`이 필요하다.
 */
export function InsightsHubNav() {
  const pathname = usePathname();

  return (
    <nav className={styles.nav} aria-label="인사이트 허브">
      {ITEMS.map((item) => (
        <Link
          key={item.href}
          className={`${styles.tab} pillActive`}
          href={item.href}
          aria-current={pathname === item.href ? "page" : undefined}
        >
          {item.label}
        </Link>
      ))}
    </nav>
  );
}
