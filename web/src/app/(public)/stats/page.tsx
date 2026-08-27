import type { Metadata } from "next";

import { getPatternStats } from "@/entities/statistics/api";
import {
  bucketTotal,
  COUNT_BUCKET_ORDER,
  orderBuckets,
  SUM_BUCKET_ORDER,
  type PatternBucket,
} from "@/entities/statistics/schema";
import { PatternDistribution } from "@/entities/statistics/ui/pattern-distribution";
import { InsightsHubNav } from "@/features/insights-hub-nav/insights-hub-nav";
import { Card } from "@/shared/ui/surface";

import styles from "./stats.module.css";

export const metadata: Metadata = {
  title: "당첨 패턴 통계",
  description:
    "역대 당첨 번호의 홀짝 구성, 고저 구성, 번호 합계 분포를 무작위 추첨에서 예상되는 형태와 비교해 보여줍니다.",
  alternates: { canonical: "/stats" },
};

/**
 * 가장 흔한 구간을 문장으로 만든다 신규 요구
 *
 * 분포표만 두면 "봤는데 무엇을 알게 됐는지 모르는" 화면이 된다. 어느 구간이 가장 흔하고
 * 그게 전체의 몇 %인지를 문장으로 먼저 말해 주면 표가 그 문장의 근거로 읽힌다.
 */
function describePeak(
  buckets: readonly PatternBucket[],
  format: (bucketKey: string) => string,
): string | null {
  const total = bucketTotal(buckets);
  if (total === 0) return null;

  const peak = buckets.reduce((best, bucket) => (bucket.count > best.count ? bucket : best));
  const share = (peak.count / total) * 100;
  return `가장 흔한 구간은 ${format(peak.bucketKey)}이고 전체의 ${share.toFixed(1)}%입니다.`;
}

function formatCountBucket(bucketKey: string): string {
  return `${bucketKey}개`;
}

function formatSumBucket(bucketKey: string): string {
  return bucketKey;
}

export default async function StatsPage() {
  const stats = await getPatternStats();

  const oddCounts = orderBuckets(stats.oddCounts, COUNT_BUCKET_ORDER);
  const highCounts = orderBuckets(stats.highCounts, COUNT_BUCKET_ORDER);
  const sumBuckets = orderBuckets(stats.sumBuckets, SUM_BUCKET_ORDER);

  return (
    <div className="stack">
      <header className="prose stack">
        <h1>당첨 패턴 통계</h1>
        <p>
          집계 대상 {stats.totalRounds}회차 기준입니다. 당첨 번호 6개가 홀수·고수를 몇 개씩
          포함했는지, 번호 합계가 어느 구간에 들어갔는지를 모은 것입니다.
        </p>
        <p>
          어느 분포든 가운데가 두껍고 양끝이 얇습니다. 이는 특정 패턴이 유리하다는 뜻이 아니라,
          6개를 무작위로 뽑으면 극단(예: 6개 모두 홀수)보다 중간 조합이 훨씬 많기 때문입니다. 여기서
          흔한 패턴을 고른다고 당첨 확률이 올라가지는 않습니다.
        </p>
      </header>

      <InsightsHubNav />

      {/* I-33: 좁은 화면에서는 세 카드가 세로로 길게 이어진다 — 데스크톱은
          이미 3열이라 문제가 없어 그 폭에서는 숨긴다(stats.module.css). */}
      <nav className={styles.toc} aria-label="빠른 이동">
        <a href="#odd-count">홀수 개수</a>
        <a href="#high-count">고수 개수</a>
        <a href="#sum">번호 합계</a>
      </nav>

      <div className={styles.groups}>
        <Card as="section" level={2}>
          <h2 id="odd-count">홀수 개수</h2>
          <p className="note">
            당첨 번호 6개 중 홀수가 몇 개였는지의 분포입니다.{" "}
            {describePeak(oddCounts, formatCountBucket)}
          </p>
          <PatternDistribution
            caption="홀수 개수별 회차 분포"
            buckets={oddCounts}
            formatBucketKey={formatCountBucket}
          />
        </Card>

        <Card as="section" level={2}>
          <h2 id="high-count">고수 개수</h2>
          <p className="note">
            23~45번을 고수로 봤을 때 6개 중 고수가 몇 개였는지의 분포입니다.{" "}
            {describePeak(highCounts, formatCountBucket)}
          </p>
          <PatternDistribution
            caption="고수 개수별 회차 분포"
            buckets={highCounts}
            formatBucketKey={formatCountBucket}
          />
        </Card>

        <Card as="section" level={2}>
          <h2 id="sum">번호 합계</h2>
          <p className="note">
            당첨 번호 6개를 더한 값의 구간별 분포입니다. 가능한 범위는 21(1~6)부터
            255(40~45)까지입니다. {describePeak(sumBuckets, formatSumBucket)}
          </p>
          <PatternDistribution
            caption="번호 합계 구간별 회차 분포"
            buckets={sumBuckets}
            formatBucketKey={formatSumBucket}
          />
        </Card>
      </div>
    </div>
  );
}
