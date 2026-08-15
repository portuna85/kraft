import { Table } from "@/shared/ui/surface";

import { bucketTotal, type PatternBucket } from "../schema";

import styles from "./pattern-distribution.module.css";

/** SVG 내부 좌표계. 실제 크기는 CSS가 정하고 여기서는 비율만 다룬다. */
const VIEWBOX_WIDTH = 100;

/**
 * 구간 분포
 *
 * 표로 그리는 이유는 이 데이터가 실제로 표이기 때문이다 — 구간·횟수·비율의 2차원 격자다.
 * 막대는 같은 값을 눈으로 비교하기 쉽게 만드는 장식이라 `aria-hidden`이고, 값은 항상
 * 셀 텍스트로도 읽힌다. 막대만 있고 숫자가 없으면 스크린리더에는 빈 표가 된다.
 *
 * 막대를 SVG로 그리는 이유는 FrequencyBar와 같다 — 데이터로 정해지는 길이를 인라인
 * style로 주면 style-src에서 'unsafe-inline'을 뺄 수 없다(§20.3).
 */
export function PatternDistribution({
  caption,
  buckets,
  formatBucketKey,
}: {
  caption: string;
  buckets: readonly PatternBucket[];
  formatBucketKey: (bucketKey: string) => string;
}) {
  const total = bucketTotal(buckets);
  const maxCount = buckets.reduce((max, bucket) => Math.max(max, bucket.count), 0);

  return (
    <Table caption={caption} captionVisible={false}>
      <thead>
        <tr>
          <th scope="col">구간</th>
          <th scope="col">회차 수</th>
          <th scope="col">비율</th>
        </tr>
      </thead>
      <tbody>
        {buckets.map((bucket) => {
          const share = total === 0 ? 0 : (bucket.count / total) * 100;
          const fillWidth = maxCount === 0 ? 0 : (bucket.count / maxCount) * VIEWBOX_WIDTH;
          // 최빈 구간 하이라이트. 어느 구간이 봉우리인지는 각 화면의 안내 문장이
          // 이미 말하고 있고(describePeak), 이건 그 문장을 표에서 눈으로 찾게 돕는다.
          const isPeak = maxCount > 0 && bucket.count === maxCount;

          return (
            <tr key={bucket.bucketKey}>
              <th scope="row">{formatBucketKey(bucket.bucketKey)}</th>
              <td>
                <span className={styles.barCell}>
                  <svg
                    className={styles.chart}
                    viewBox={`0 0 ${VIEWBOX_WIDTH} 8`}
                    preserveAspectRatio="none"
                    aria-hidden="true"
                  >
                    <rect
                      className={styles.track}
                      x="0"
                      y="0"
                      width={VIEWBOX_WIDTH}
                      height="8"
                      rx="4"
                    />
                    <rect
                      className={`${styles.fill} ${isPeak ? styles.fillPeak : ""}`}
                      x="0"
                      y="0"
                      width={fillWidth}
                      height="8"
                      rx="4"
                    />
                  </svg>
                  <span className={`${styles.count} ${isPeak ? styles.countPeak : ""}`}>
                    {bucket.count}회
                  </span>
                </span>
              </td>
              <td>{share.toFixed(1)}%</td>
            </tr>
          );
        })}
      </tbody>
    </Table>
  );
}
