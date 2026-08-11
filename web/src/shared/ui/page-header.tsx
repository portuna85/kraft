import type { PageHeaderContract } from "./contracts";
import styles from "./page-header.module.css";

/**
 * PageHeader — improvement_fe.md §23.14
 *
 * `title`이 그 페이지의 `<h1>`이다. 호출부가 따로 `<h1>`을 두지 않는다 — 두면
 * 페이지에 제목이 둘 생긴다.
 */
export function PageHeader({ eyebrow, title, description, actions }: PageHeaderContract) {
  return (
    <header className={styles.header}>
      <div className={styles.titleGroup}>
        {eyebrow !== undefined && <p className={styles.eyebrow}>{eyebrow}</p>}
        <h1 className={styles.title}>{title}</h1>
        {description !== undefined && <div className={styles.description}>{description}</div>}
      </div>
      {actions !== undefined && <div className={styles.actions}>{actions}</div>}
    </header>
  );
}
