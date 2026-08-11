import type { StatContract } from "./contracts";
import styles from "./stat.module.css";

/**
 * Stat — improvement_fe.md §23.14
 *
 * 요약 그리드의 키/값 타일 하나. 여러 개를 감싸는 그리드 컨테이너는 호출부가
 * 자기 CSS 모듈로 만든다 — 화면마다 열 배치가 달라질 수 있어 여기서 강제하지 않는다.
 */
export function Stat({ label, value, tone = "neutral" }: StatContract) {
  return (
    <div className={styles.stat}>
      <p className={styles.label}>{label}</p>
      <p className={`${styles.value} ${styles[tone]}`}>{value}</p>
    </div>
  );
}
