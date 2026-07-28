import styles from "./stats.module.css";

export default function Loading() {
  return (
    <section className="panel">
      <div className="skeleton-line skeleton-eyebrow" />
      <div className="skeleton-line skeleton-h1" style={{ marginTop: "10px" }} />
      <div className="skeleton-line skeleton-body" style={{ marginTop: "14px", width: "40%" }} />
      {[0, 1, 2].map((s) => (
        <div key={s} className={styles.section}>
          <div className="skeleton-line skeleton-body" style={{ width: "160px", marginBottom: "14px" }} />
          <ul className={styles.list}>
            {Array.from({ length: 5 }).map((_, i) => (
              <li key={i} className={styles.item}>
                <div className="skeleton-line skeleton-caption" style={{ width: "60px" }} />
                <div className="skeleton-line" style={{ height: "8px", borderRadius: "999px" }} />
                <div className="skeleton-line skeleton-caption" style={{ width: "40px" }} />
                <div className="skeleton-line skeleton-caption" style={{ width: "40px" }} />
              </li>
            ))}
          </ul>
        </div>
      ))}
    </section>
  );
}
