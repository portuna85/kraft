import type { Metadata } from "next";
import { headers } from "next/headers";
import { JsonLdBreadcrumb } from "@/components/json-ld";
import { getPatternStats, getPublicBaseUrl, type PatternBucket } from "@/lib/api";
import { logCoreDataFailure } from "@/lib/logger";

export const metadata: Metadata = {
  title: "패턴 통계",
  description: "로또 6/45 당첨 번호의 홀짝 비율, 고저 분포, 합계 구간별 빈도를 확인할 수 있습니다.",
  alternates: { canonical: "/stats" },
};

const SUM_ORDER = ["21-65", "66-110", "111-155", "156-200", "201-255"];

function PatternSection({
  title,
  buckets,
  totalRounds,
  sortOrder,
}: {
  title: string;
  buckets: PatternBucket[];
  totalRounds: number;
  sortOrder?: string[];
}) {
  const sorted = sortOrder
    ? [...buckets].sort((a, b) => sortOrder.indexOf(a.bucketKey) - sortOrder.indexOf(b.bucketKey))
    : [...buckets].sort((a, b) => Number(a.bucketKey) - Number(b.bucketKey));
  const maxCount = Math.max(...sorted.map((bucket) => bucket.count), 1);

  return (
    <div className="pattern-section">
      <h2 className="section-title">{title}</h2>
      <ul className="pattern-list">
        {sorted.map((bucket) => {
          const pct = totalRounds > 0 ? ((bucket.count / totalRounds) * 100).toFixed(1) : "0.0";
          const barWidth = Math.round((bucket.count / maxCount) * 100);

          return (
            <li key={bucket.bucketKey} className="pattern-item">
              <span className="pattern-key">{bucket.bucketKey}</span>
              <div className="bar-track">
                <div className="bar-fill" style={{ width: `${barWidth}%` }} />
              </div>
              <span className="pattern-count">{bucket.count}회</span>
              <span className="pattern-pct">{pct}%</span>
            </li>
          );
        })}
      </ul>
    </div>
  );
}

export default async function StatsPage() {
  const nonce = (await headers()).get("x-nonce") ?? undefined;
  const baseUrl = getPublicBaseUrl();

  // 이 페이지의 유일한 핵심 데이터 — 실패를 200 "준비 중" 폴백으로 숨기지 않고
  // error.tsx(5xx)로 넘긴다(F4: /frequency, /companion과 동일한 정책).
  let stats;
  try {
    stats = await getPatternStats();
  } catch (error) {
    logCoreDataFailure(error, "패턴 통계 조회 실패 — 핵심 데이터 실패로 페이지 오류 처리");
    throw error;
  }

  return (
    <section className="panel">
      <JsonLdBreadcrumb baseUrl={baseUrl} nonce={nonce} items={[{ name: "패턴 통계", item: `${baseUrl}/stats` }]} />
      <p className="eyebrow">패턴 통계</p>
      <h1 className="page-title">패턴 통계</h1>
      <p className="muted stats-summary">총 {stats.totalRounds}회 기준</p>
      <PatternSection title="홀수 개수 분포" buckets={stats.oddCounts} totalRounds={stats.totalRounds} />
      <PatternSection title="고번호 개수 분포" buckets={stats.highCounts} totalRounds={stats.totalRounds} />
      <PatternSection
        title="합계 구간 분포"
        buckets={stats.sumBuckets}
        totalRounds={stats.totalRounds}
        sortOrder={SUM_ORDER}
      />
    </section>
  );
}
