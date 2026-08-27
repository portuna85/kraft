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
    // 표가 뷰포트보다 넓으면 가로 스크롤이 필요한데, Chrome·Firefox와 달리 Safari는
    // 스크롤 가능한 컨테이너를 자동으로 키보드 포커스 대상에 넣지 않는다 — tabIndex와
    // 접근 이름 없이는 키보드 사용자가 가려진 열에 도달할 방법이 없다. caption을 그대로
    // region의 이름으로 재사용해 새 prop 없이 해결한다.
    <div className={styles.tableWrap} tabIndex={0} role="region" aria-label={caption}>
      <table className={styles.table}>
        <caption className={captionVisible ? styles.caption : "sr-only"}>{caption}</caption>
        {children}
      </table>
    </div>
  );
}

/**
 * MetricCard — kraft-redesign-plan.md §7 "Metric Card"
 *
 * `<dl>` fact 목록 대신 쓰는 개별 지표 타일. 라벨/값을 `<dt>`/`<dd>`가 아니라 `<p>`로
 * 두는 이유는 여러 MetricCard를 늘어놓았을 때 그 자체가 하나의 정의 목록으로 읽힐
 * 필요가 없어서다 — 각 카드는 독립된 카드일 뿐, 호출부가 필요하면 바깥에서
 * `role="list"`/`<dl>`로 감싸면 된다.
 */
export function MetricCard({
  label,
  value,
  hint,
}: {
  label: string;
  value: ReactNode;
  hint?: string;
}) {
  return (
    <Card as="div" level={2}>
      <p className={`${styles.metricLabel} note`}>{label}</p>
      <p className={styles.metricValue}>{value}</p>
      {hint !== undefined && <p className={styles.metricHint}>{hint}</p>}
    </Card>
  );
}

/** MetricCard 여러 개를 반응형 그리드로 늘어놓는다. */
export function MetricCardGrid({ children }: { children: ReactNode }) {
  return <div className={styles.metricGrid}>{children}</div>;
}

export type PageLinkBuilder = (page: number) => string;

/**
 * KF-15(docs/improvement.md): 경계에서 `aria-disabled`+`pointer-events:none`을
 * 준 `<Link>`는 여전히 유효한 href를 가진 네이티브 링크라 탭 순서에 남고 Enter로
 * 진짜 이동했다 — ARIA/CSS는 시맨틱을 못 바꾼다. 비활성 상태는 아예 링크가 아닌
 * `<span>`으로 렌더해 탭 순서·Enter 활성화 자체를 없앤다. 같은 클래스를 재사용해
 * 시각적으로는 동일하게 유지한다.
 */
function PageLink({
  disabled,
  href,
  children,
}: {
  disabled: boolean;
  href: string;
  children: ReactNode;
}) {
  if (disabled) {
    return (
      <span className={`${styles.pageLink} ${styles.pageDisabled}`} aria-disabled="true">
        {children}
      </span>
    );
  }
  return (
    <Link className={styles.pageLink} href={href}>
      {children}
    </Link>
  );
}

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
      <PageLink disabled={isFirst} href={buildHref(0)}>
        처음
      </PageLink>
      <PageLink disabled={isFirst} href={buildHref(Math.max(page - 1, 0))}>
        이전
      </PageLink>

      <span className={`${styles.pageLink} pillActive`} aria-current="page">
        {page + 1} / {totalPages}
      </span>

      <PageLink disabled={isLast} href={buildHref(Math.min(page + 1, totalPages - 1))}>
        다음
      </PageLink>
      <PageLink disabled={isLast} href={buildHref(totalPages - 1)}>
        마지막
      </PageLink>
    </nav>
  );
}
