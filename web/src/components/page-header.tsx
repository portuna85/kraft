import type { ReactNode } from "react";
import styles from "./page-header.module.css";

type PageHeaderProps = {
  eyebrow?: string;
  title: ReactNode;
  description?: ReactNode;
  meta?: ReactNode;
  actions?: ReactNode;
  className?: string;
};

/**
 * 페이지의 제목, 설명, 주요 행동을 같은 순서와 간격으로 제공한다.
 * 제목은 페이지마다 하나의 h1만 소유하도록 이 컴포넌트에서 고정한다.
 */
export function PageHeader({ eyebrow, title, description, meta, actions, className }: PageHeaderProps) {
  return (
    <header className={`${styles.header}${className ? ` ${className}` : ""}`}>
      <div className={styles.copy}>
        {eyebrow ? <p className="eyebrow">{eyebrow}</p> : null}
        <h1 className="page-title">{title}</h1>
        {description ? <div className="page-subtitle">{description}</div> : null}
        {meta ? <div className={styles.meta}>{meta}</div> : null}
      </div>
      {actions ? <div className={styles.actions}>{actions}</div> : null}
    </header>
  );
}
