import styles from "./data-page-loading.module.css";

// FE-006: 루트 loading.tsx가 "home" 고정이라 전용 loading.tsx가 없는 모든 라우트
// (/community, /info/*, /saved, /analysis, /status, /ops …)에 로또 공 스켈레톤이 떴다.
// "generic"은 도메인 형태를 암시하지 않는 중립 폴백이다.
type Variant = "home" | "bars" | "list" | "generic";

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
        ) : variant === "generic" ? (
          // 어떤 콘텐츠가 올지 모르는 폴백이라 본문 블록 몇 줄만 그린다.
          <div className={styles.rows}>
            {Array.from({ length: 6 }, (_, index) => (
              <div key={index} className="skeleton-line skeleton-body" />
            ))}
          </div>
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
