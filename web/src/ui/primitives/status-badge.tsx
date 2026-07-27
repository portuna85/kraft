import type { StatusBadgeContract, StatusBadgeStatus } from "./contracts";
import styles from "./status-badge.module.css";

const STATUS_CLASS: Record<StatusBadgeStatus, string> = {
  fresh: styles.fresh,
  stale: styles.stale,
  error: styles.error,
};

export function StatusBadge({ status, label }: StatusBadgeContract) {
  return (
    <span className={`${styles.badge} ${STATUS_CLASS[status]}`}>
      <span className={styles.dot} aria-hidden="true" />
      {label}
    </span>
  );
}
