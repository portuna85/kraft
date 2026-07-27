import type { BadgeContract, BadgeTone } from "./contracts";
import styles from "./badge.module.css";

const TONE_CLASS: Record<BadgeTone, string> = {
  neutral: styles.neutral,
  brand: styles.brand,
  success: styles.success,
  warning: styles.warning,
  danger: styles.danger,
};

export function Badge({ tone, children }: BadgeContract) {
  return <span className={`${styles.badge} ${TONE_CLASS[tone]}`}>{children}</span>;
}
