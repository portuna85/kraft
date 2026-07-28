import styles from "./frequency.module.css";

export default function Loading() {
  return (
    <section className="panel">
      <div className="skeleton-line skeleton-eyebrow" />
      <div className="skeleton-line skeleton-h1" style={{ marginTop: "10px" }} />
      <div className="skeleton-line skeleton-body" style={{ marginTop: "14px", width: "50%" }} />
      <div className="freq-summary" style={{ marginTop: "24px" }}>
        {[100, 120].map((w, i) => (
          <div key={i} className={styles.rankGroup}>
            <div className="skeleton-line skeleton-caption" style={{ width: `${w}px` }} />
            <div className="skeleton-balls" style={{ marginTop: "10px" }}>
              {Array.from({ length: 5 }).map((_, j) => (
                <div key={j} className="skeleton-ball skeleton-ball-sm" />
              ))}
            </div>
          </div>
        ))}
      </div>
      <div className={styles.grid} style={{ marginTop: "28px" }}>
        {Array.from({ length: 20 }).map((_, i) => (
          <div key={i} className={styles.item}>
            <div className="skeleton-ball skeleton-ball-sm" />
            <div className="skeleton-line" style={{ height: "7px", borderRadius: "999px" }} />
            <div className="skeleton-line skeleton-caption" style={{ width: "40px" }} />
            <div className="skeleton-line skeleton-caption" style={{ width: "36px" }} />
          </div>
        ))}
      </div>
    </section>
  );
}
