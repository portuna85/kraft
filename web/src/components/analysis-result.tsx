import type { AnalysisResponse } from "@/lib/api";
import { FirstPrizeHistoryList } from "@/components/first-prize-history";
import styles from "@/app/analysis/analysis.module.css";

type AnalysisResultProps = {
  analysis: AnalysisResponse;
  title: string;
};

export function AnalysisResult({ analysis, title }: AnalysisResultProps) {
  return (
    <div className={styles.result}>
      <h2 className="section-title">{title}</h2>

      <FirstPrizeHistoryList history={analysis.firstPrizeHistory} />

      <div className={styles.resultGrid}>
        <div className={styles.resultCell}>
          <span className={styles.resultLabel}>홀수 / 짝수</span>
          <span className={styles.resultValue}>{analysis.oddCount} / {analysis.evenCount}</span>
        </div>
        <div className={styles.resultCell}>
          <span className={styles.resultLabel}>저번호 / 고번호</span>
          <span className={styles.resultValue}>{analysis.lowCount} / {analysis.highCount}</span>
        </div>
        <div className={styles.resultCell}>
          <span className={styles.resultLabel}>합계</span>
          <span className={styles.resultValue}>{analysis.sumOfNumbers}</span>
          <span className={styles.resultSub}>{analysis.sumBucket} 구간</span>
        </div>
        <div className={styles.resultCell}>
          <span className={styles.resultLabel}>연속 번호</span>
          <span className={styles.resultValue}>{analysis.consecutivePairCount}쌍</span>
        </div>
      </div>

      <div>
        <p className={`section-title ${styles.sectionTitle}`}>구간 분포</p>
        <ul className={styles.rangeDistList}>
          {analysis.rangeDistribution.map((range) => (
            <li key={range.range} className={styles.rangeDistItem}>
              <span className={styles.rangeLabel}>{range.range}</span>
              <div className="bar-track">
                <div
                  className="bar-fill"
                  style={{ width: `${Math.round((range.count / 6) * 100)}%` }}
                />
              </div>
              <span className={styles.rangeCount}>{range.count}</span>
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}
