import Link from "next/link";
import type { ReactNode } from "react";

import styles from "./surface.module.css";

/**
 * Card
 *
 * 상호작용하는 카드는 만들지 않는다. 카드 전체를 클릭 가능하게 하려면 안에 링크나
 * 버튼을 두고 그것이 접근 이름을 갖게 한다 — div에 onClick을 붙이면 키보드로 도달할 수
 * 없고, 도달하게 만들려고 tabIndex와 keydown을 흉내내기 시작하면 버튼을 다시 만드는 셈이다.
 */
export function Card({
  level = 1,
  as: Tag = "div",
  children,
}: {
  level?: 1 | 2 | 3;
  as?: "div" | "article" | "section" | "li";
  children: ReactNode;
}) {
  const levelClass = level === 3 ? styles.level3 : level === 2 ? styles.level2 : styles.level1;
  return <Tag className={`${styles.card} ${levelClass}`}>{children}</Tag>;
}

/**
 * Table — `caption`이 필수다. 표에 이름이 없으면 스크린리더 사용자는 표 목록에서
 * 무엇에 관한 표인지 알 수 없다. 열 머리글은 호출부가 `<th scope>`로 준다.
 */
export function Table({
  caption,
  children,
  captionVisible = true,
}: {
  caption: string;
  children: ReactNode;
  captionVisible?: boolean;
}) {
  return (
    <div className={styles.tableWrap}>
      <table className={styles.table}>
        <caption className={captionVisible ? styles.caption : "sr-only"}>{caption}</caption>
        {children}
      </table>
    </div>
  );
}

export type PageLinkBuilder = (page: number) => string;

/**
 * Pagination — 처음·이전·다음·마지막을 **전부** 제공한다(레거시 FE-052).
 *
 * 이전/다음만 있으면 100페이지짜리 목록에서 마지막으로 가는 방법이 없다. 링크로 만드는
 * 이유는 URL이 목록 상태의 단일 진실 공급원이기 때문이다(§5.6) — 공유되고, 뒤로가기가
 * 동작하고, JS 없이도 넘어간다.
 *
 * page는 0-based(백엔드 계약)이고 화면 표기는 1-based다.
 */
export function Pagination({
  page,
  totalPages,
  buildHref,
}: {
  page: number;
  totalPages: number;
  buildHref: PageLinkBuilder;
}) {
  if (totalPages <= 1) return null;

  const isFirst = page <= 0;
  const isLast = page >= totalPages - 1;

  return (
    <nav className={styles.pagination} aria-label="페이지 이동">
      <Link
        className={`${styles.pageLink} ${isFirst ? styles.pageDisabled : ""}`}
        href={buildHref(0)}
        aria-disabled={isFirst || undefined}
      >
        처음
      </Link>
      <Link
        className={`${styles.pageLink} ${isFirst ? styles.pageDisabled : ""}`}
        href={buildHref(Math.max(page - 1, 0))}
        aria-disabled={isFirst || undefined}
      >
        이전
      </Link>

      <span className={styles.pageLink} aria-current="page">
        {page + 1} / {totalPages}
      </span>

      <Link
        className={`${styles.pageLink} ${isLast ? styles.pageDisabled : ""}`}
        href={buildHref(Math.min(page + 1, totalPages - 1))}
        aria-disabled={isLast || undefined}
      >
        다음
      </Link>
      <Link
        className={`${styles.pageLink} ${isLast ? styles.pageDisabled : ""}`}
        href={buildHref(totalPages - 1)}
        aria-disabled={isLast || undefined}
      >
        마지막
      </Link>
    </nav>
  );
}
