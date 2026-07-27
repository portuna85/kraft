import type { SkeletonContract } from "./contracts";
import styles from "./skeleton.module.css";

export function Skeleton({ lines = 1, variant = "text" }: SkeletonContract) {
  if (variant === "card") {
    return <div className={`${styles.shimmer} ${styles.card}`} aria-hidden="true" />;
  }
  if (variant === "ball") {
    return <div className={`${styles.shimmer} ${styles.ball}`} aria-hidden="true" />;
  }
  return (
    <div className={styles.lines} aria-hidden="true">
      {Array.from({ length: lines }, (_, i) => (
        <div key={i} className={`${styles.shimmer} ${styles.line}`} />
      ))}
    </div>
  );
}
