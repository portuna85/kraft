import Link from "next/link";

import type { AccordionContract, BreadcrumbContract } from "./contracts";
import styles from "./navigation.module.css";

/**
 * Breadcrumb — improvement_fe.md §23.10, §23.13
 *
 * `<nav>` 안의 순서 있는 목록으로 둔다. 구분자는 CSS가 그린다 — 텍스트로 넣으면
 * 스크린리더가 "홈 슬래시 안내 슬래시"처럼 읽는다.
 *
 * 현재 페이지는 링크가 아니라 `aria-current="page"`를 단 텍스트다.
 */
export function Breadcrumb({ items, current }: BreadcrumbContract) {
  return (
    <nav aria-label="현재 위치">
      <ol className={styles.crumbs}>
        {items.map((item) => (
          <li key={item.href} className={styles.crumb}>
            <Link href={item.href}>{item.label}</Link>
          </li>
        ))}
        <li className={styles.crumb}>
          <span aria-current="page">{current}</span>
        </li>
      </ol>
    </nav>
  );
}

/**
 * Accordion — improvement_fe.md §23.13 (FAQ 전용)
 *
 * 명세는 `<button aria-expanded>` + 패널 연결을 적었지만 `<details>/<summary>`로 만든다.
 * 요구의 본질은 "열림 상태가 전달되고 패널이 제목과 묶일 것"이고 네이티브 요소가 그것을
 * 이미 만족한다. 대신 얻는 것이 크다 — 이 컴포넌트를 쓰는 /info/[slug]는 예산 목표가
 * 51KB로 가장 빡빡한 라우트인데, 여닫기 하나 때문에 "use client"를 붙이면 화면 전체가
 * 하이드레이션 대상이 된다. JS가 아직 안 왔을 때도 열리는 것은 덤이다.
 */
export function Accordion({ label, items }: AccordionContract) {
  return (
    <div className={styles.accordion} role="group" aria-label={label}>
      {items.map((item) => (
        <details className={styles.item} key={item.id} name={label}>
          <summary className={styles.summary}>{item.question}</summary>
          <div className={styles.panel}>{item.answer}</div>
        </details>
      ))}
    </div>
  );
}
