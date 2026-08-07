import type { BadgeContract, BadgeTone } from "./contracts";
import styles from "./badge.module.css";

// CSS Modules 클래스명은 badge.module.css에 실제로 존재함이 빌드 타임에 보장된다.
const TONE_CLASS: Record<BadgeTone, string> = {
  neutral: styles.neutral!,
  brand: styles.brand!,
  success: styles.success!,
  warning: styles.warning!,
  danger: styles.danger!,
};

export function Badge({ tone, children }: BadgeContract) {
  return <span className={`${styles.badge} ${TONE_CLASS[tone]}`}>{children}</span>;
}
