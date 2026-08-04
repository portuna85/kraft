import type { InlineAlertContract, ToastTone } from "./contracts";
import styles from "./inline-alert.module.css";

// CSS Modules 클래스명은 inline-alert.module.css에 실제로 존재함이 빌드 타임에 보장된다.
const TONE_CLASS: Record<ToastTone, string> = {
  neutral: styles.neutral!,
  success: styles.success!,
  danger: styles.danger!,
};

export function InlineAlert({ tone, title, description }: InlineAlertContract) {
  return (
    <div className={`${styles.alert} ${TONE_CLASS[tone]}`} role={tone === "danger" ? "alert" : "status"}>
      <span className={styles.title}>{title}</span>
      {description && <span>{description}</span>}
    </div>
  );
}
