import styles from "./feedback.module.css";
import type { BadgeContract, StatusBadgeContract } from "./contracts";

/**
 * Badge — improvement_fe.md §9.3
 * `label`이 필수라 색상만으로 의미를 전달하는 배지를 만들 수 없다.
 */
export function Badge({ tone = "neutral", size = "md", label }: BadgeContract) {
  return (
    <span className={`${styles.badge} ${styles[tone]} ${size === "sm" ? styles.badgeSm : ""}`}>
      {label}
    </span>
  );
}

const STATUS_TONE = {
  fresh: "success",
  stale: "warning",
  error: "danger",
} as const;

/**
 * StatusBadge — 데이터 신선도(§3.1).
 *
 * 점은 `aria-hidden` 장식이고 의미는 텍스트가 전달한다. 색상 단독 전달을 금지하는
 * 이유는 색각 이상뿐 아니라 흑백 출력·저대비 화면에서도 배지가 무의미해지기 때문이다.
 */
export function StatusBadge({ status, label }: StatusBadgeContract) {
  const tone = STATUS_TONE[status];
  return (
    <span className={`${styles.badge} ${styles[tone]}`}>
      <span className={styles.dot} aria-hidden="true" />
      {label}
    </span>
  );
}
