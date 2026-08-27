"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

import { ROUTES } from "@/shared/config/routes";

import styles from "./saved-history-nav.module.css";

const ITEMS = [
  { href: ROUTES.saved, label: "저장한 번호" },
  { href: ROUTES.recommendHistory, label: "추천 이력" },
] as const;

/**
 * 보관함 워크스택 탭
 *
 * kraft-redesign-plan.md §6 "Saved and History"는 두 화면을 "한 워크스페이스"로
 * 묶으라고 요구한다. 저장 번호와 추천 이력은 서로 다른 엔티티라(저장 번호에는
 * `strategy`가 없다) 목록 자체를 하나로 합치거나 공유 필터를 만들 수는 없다 —
 * 그건 백엔드가 두 엔티티를 아우르는 새 계약을 줘야 가능한 일이라 프론트 단독
 * 세션 범위를 벗어난다. 대신 두 화면이 **같은 자리에서 서로를 오가는 탭처럼
 * 보이게** 한다 — `/frequency`의 기간 필터 pill과 같은 패턴
 * (`frequency.module.css` `.period` + 전역 `pillActive`)을 그대로 재사용해
 * 새 시각 언어를 만들지 않는다.
 *
 * `PrimaryNav`/`TabBar`와 같은 이유로 클라이언트 컴포넌트다 — 현재 라우트
 * 판정에 `usePathname`이 필요하다.
 */
export function SavedHistoryNav() {
  const pathname = usePathname();

  return (
    <nav className={styles.nav} aria-label="보관함 워크스페이스">
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
