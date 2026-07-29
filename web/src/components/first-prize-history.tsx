import type { FirstPrizeHistory } from "@/lib/api";
import { formatCurrency, formatDrawDate } from "@/lib/format";
import styles from "@/components/first-prize-history.module.css";

type FirstPrizeHistoryProps = {
  history: FirstPrizeHistory[];
  compact?: boolean;
};

export function FirstPrizeHistoryList({ history, compact = false }: FirstPrizeHistoryProps) {
  if (history.length === 0) {
    return <p className={`${styles.summary} ${styles.noHistory}`}>역대 1등 당첨 이력 없음</p>;
  }

  return (
    <div className={styles.history}>
      <p className={`${styles.summary} ${styles.hasHistory}`}>
        역대 1등 당첨 이력 {history.length}건
      </p>
      <ul className={styles.list}>
        {history.map((item) => (
          <li key={item.round} className={styles.item}>
            <strong>{item.round}회</strong>
            <time dateTime={item.drawDate}>{formatDrawDate(item.drawDate)}</time>
            {!compact ? <span>1등 당첨금 {formatCurrency(item.firstPrizeAmount)}</span> : null}
          </li>
        ))}
      </ul>
    </div>
  );
}
