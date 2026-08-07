import type { StatusBadgeContract, StatusBadgeStatus } from "./contracts";
import styles from "./status-badge.module.css";

// CSS Modules 클래스명은 status-badge.module.css에 실제로 존재함이 빌드 타임에 보장된다.
const STATUS_CLASS: Record<StatusBadgeStatus, string> = {
  fresh: styles.fresh!,
  stale: styles.stale!,
  error: styles.error!,
};

export function StatusBadge({ status, label }: StatusBadgeContract) {
  return (
    <span className={`${styles.badge} ${STATUS_CLASS[status]}`}>
      <span className={styles.dot} aria-hidden="true" />
      {label}
    </span>
  );
}
