import styles from "./companion.module.css";

export default function Loading() {
  return (
    <section className="panel">
      <div className="skeleton-line skeleton-eyebrow" />
      <div className="skeleton-line skeleton-h1" style={{ marginTop: "10px" }} />
      <div className="skeleton-line skeleton-body" style={{ marginTop: "14px", width: "55%" }} />
      <ol className={styles.list}>
        {Array.from({ length: 15 }).map((_, i) => (
          <li key={i} className={styles.item}>
            <div className="skeleton-line skeleton-caption" style={{ width: "20px" }} />
            <div className={styles.pairBalls}>
              <div className="skeleton-ball skeleton-ball-sm" />
              <div className="skeleton-line skeleton-caption" style={{ width: "12px" }} />
              <div className="skeleton-ball skeleton-ball-sm" />
            </div>
            <div className={styles.pairInfo}>
              <div className="skeleton-line skeleton-caption" style={{ width: "70px" }} />
              <div className="skeleton-line skeleton-caption" style={{ width: "40px", marginTop: "4px" }} />
            </div>
          </li>
        ))}
      </ol>
    </section>
  );
}
