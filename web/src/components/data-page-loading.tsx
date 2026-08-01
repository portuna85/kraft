import styles from "./data-page-loading.module.css";

type Variant = "home" | "bars" | "list";

export function DataPageLoading({ variant = "bars" }: { variant?: Variant }) {
  return (
    <div className={styles.page} aria-busy="true" aria-label="콘텐츠를 불러오는 중">
      <section className={`panel ${styles.panel}`}>
        <div className={styles.header}>
          <div className={`skeleton-line skeleton-eyebrow ${styles.eyebrow}`} />
          <div className={`skeleton-line ${styles.title}`} />
          <div className={`skeleton-line skeleton-body ${styles.description}`} />
        </div>
        {variant === "home" ? (
          <>
            <div className={`skeleton-balls ${styles.balls}`}>
              {Array.from({ length: 7 }, (_, index) => <div key={index} className="skeleton-ball" />)}
            </div>
            <div className={`skeleton-line ${styles.largeBlock}`} />
          </>
        ) : (
          <div className={styles.rows}>
            {Array.from({ length: variant === "list" ? 12 : 15 }, (_, index) => (
              <div key={index} className={styles.row}>
                <div className="skeleton-line skeleton-caption" />
                <div className={`skeleton-line ${styles.bar}`} />
                <div className="skeleton-line skeleton-caption" />
              </div>
            ))}
          </div>
        )}
      </section>
      {variant === "home" ? (
        <section className={styles.cards}>
          {Array.from({ length: 3 }, (_, index) => (
            <div key={index} className={styles.card}>
              <div className="skeleton-line skeleton-eyebrow" />
              <div className="skeleton-line skeleton-body" />
              <div className={`skeleton-line skeleton-body ${styles.short}`} />
            </div>
          ))}
        </section>
      ) : null}
    </div>
  );
}
