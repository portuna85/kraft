"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

import { DropdownMenu } from "@/shared/ui/dropdown-menu";

import { currentNavHref, INSIGHTS_GROUP_TITLE, PRIMARY_NAV } from "./nav-items";
import styles from "./shell.module.css";

/**
 * KF-25②(docs/improvement.md): 데스크톱 내비게이션이 현재 라우트를 노출하지
 * 않았다 — 모바일 TabBar는 `aria-current`·활성 스타일을 갖췄는데 뷰포트별로
 * 동작이 달랐다. TabBar와 정확히 같은 패턴("use client"를 잎에 가깝게,
 * §19.2 P-8) — `SiteHeader` 자체는 서버 컴포넌트로 남기고 이 리프만 pathname을
 * 읽는다.
 *
 * kraft-redesign-plan.md P0: 9개 최상위 항목을 4대 축(번호 뽑기·진단·인사이트·
 * 커뮤니티)으로 줄인다. "인사이트" 그룹만 항목 수가 많아(6개) 평면으로 두면
 * 줄어든 항목 수를 다시 늘리는 셈이므로, 그 그룹만 `DropdownMenu`로 접는다 —
 * 나머지 그룹은 항목이 1~2개뿐이라 그대로 평면 링크로 둔다.
 */
export function PrimaryNav() {
  const pathname = usePathname();
  const allItems = PRIMARY_NAV.flatMap((group) => group.items);
  // RSP-23(docs/improvement.md): 항목마다 boolean을 따로 계산하면 부모/자식이
  // 같은 메뉴에 있을 때 둘 다 현재가 된다(`/recommend/history`). 메뉴 전체에서
  // 하나를 고른 뒤 그것과 같은지만 본다. 접힌 "인사이트" 항목도 이 계산에
  // 포함시켜야 드롭다운 트리거의 활성 상태를 같은 기준으로 판정할 수 있다.
  const currentHref = currentNavHref(allItems, pathname);

  const insightsGroup = PRIMARY_NAV.find((group) => group.title === INSIGHTS_GROUP_TITLE);
  const insightsActive = insightsGroup?.items.some((item) => item.href === currentHref) ?? false;

  return (
    <nav className={styles.primaryNav} aria-label="주요 메뉴">
      {PRIMARY_NAV.map((group) =>
        group.title === INSIGHTS_GROUP_TITLE ? (
          <DropdownMenu
            key={group.title}
            aria-label="인사이트 메뉴"
            trigger={(props) => (
              <button
                type="button"
                className={`${styles.navLink} ${styles.navMenuTrigger}`}
                data-active={insightsActive ? "true" : undefined}
                {...props}
              >
                {group.title}
              </button>
            )}
            items={group.items.map((item) => ({ label: item.label, href: item.href }))}
          />
        ) : (
          group.items.map((item) => (
            <Link
              key={item.href}
              className={styles.navLink}
              href={item.href}
              aria-current={item.href === currentHref ? "page" : undefined}
            >
              {item.label}
            </Link>
          ))
        ),
      )}
    </nav>
  );
}
