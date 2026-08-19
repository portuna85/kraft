import type { Metadata } from "next";

import { analyzeCombination } from "@/entities/statistics/api";
import {
  COMBINATION_SIZE,
  isCompleteCombination,
  parseCombinationInput,
} from "@/entities/statistics/combination";
import type { CombinationAnalysis } from "@/entities/statistics/schema";
import { LottoBallSet } from "@/entities/round/ui/lotto-ball";
import { PrizeTable } from "@/entities/round/ui/prize-table";
import { CombinationPicker } from "@/features/combination-analysis/combination-picker";
import { Card } from "@/shared/ui/surface";
import { EmptyState } from "@/shared/ui/states";

import styles from "./analysis.module.css";

export const metadata: Metadata = {
  title: "내 조합 진단",
  description:
    "번호 6개를 고르면 홀짝·고저 구성, 합계 구간, 연속 번호와 역대 1등 당첨 이력을 함께 확인할 수 있습니다.",
  alternates: { canonical: "/analysis" },
};

export default async function AnalysisPage({
  searchParams,
}: {
  searchParams: Promise<{ numbers?: string }>;
}) {
  const { numbers: raw } = await searchParams;
  const numbers = parseCombinationInput(raw ?? "");

  // 조합이 완성됐을 때만 서버에 묻는다. 덜 고른 상태로 요청하면 백엔드가 400을 낸다.
  const analysis = isCompleteCombination(numbers) ? await analyzeCombination(numbers) : null;

  return (
    <div className="stack">
      <header className="prose stack">
        <h1>내 조합 진단</h1>
        <p>
          번호 {COMBINATION_SIZE}개를 고르면 그 조합의 구성과 역대 1등 당첨 이력을 보여줍니다. 진단
          결과는 주소창에 남으므로 링크로 공유할 수 있습니다.
        </p>
        <p>
          어떤 조합이든 당첨 확률은 8,145,060분의 1로 같습니다. 이 화면은 조합의 특징을 설명할 뿐,
          더 좋은 조합을 가려내지 않습니다.
        </p>
      </header>

      <div className={styles.layout}>
        <section aria-labelledby="picker" className="stack">
          <h2 id="picker">번호 고르기</h2>
          <CombinationPicker initialNumbers={numbers} />
        </section>

        <section aria-labelledby="result" className="stack">
          <h2 id="result">진단 결과</h2>
          {analysis === null ? (
            <EmptyState
              reason="no-data"
              title="아직 진단할 조합이 없습니다"
              description={`번호 ${COMBINATION_SIZE}개를 고르고 분석하기를 누르면 결과가 여기에 표시됩니다.`}
            />
          ) : (
            <AnalysisResult analysis={analysis} />
          )}
        </section>
      </div>
    </div>
  );
}

function AnalysisResult({ analysis }: { analysis: CombinationAnalysis }) {
  return (
    <div className="stack">
      <LottoBallSet numbers={analysis.numbers} />

      <Card as="section" level={2}>
        <h3>조합 구성</h3>
        <dl className={styles.facts}>
          <div className={styles.fact}>
            <dt>홀수 / 짝수</dt>
            <dd>
              {analysis.oddCount} / {analysis.evenCount}
            </dd>
          </div>
          <div className={styles.fact}>
            <dt>저수 / 고수</dt>
            <dd>
              {analysis.lowCount} / {analysis.highCount}
            </dd>
          </div>
          <div className={styles.fact}>
            <dt>번호 합계</dt>
            <dd>
              {analysis.sumOfNumbers} ({analysis.sumBucket} 구간)
            </dd>
          </div>
          <div className={styles.fact}>
            <dt>연속한 번호 쌍</dt>
            <dd>{analysis.consecutivePairCount}쌍</dd>
          </div>
        </dl>
        <p className="note">
          저수는 1~22번, 고수는 23~45번입니다. 이 값들은 조합의 생김새를 설명할 뿐 당첨 가능성과는
          무관합니다.
        </p>
      </Card>

      {analysis.rangeDistribution.length > 0 && (
        <Card as="section" level={2}>
          <h3>구간별 분포</h3>
          <dl className={styles.facts}>
            {analysis.rangeDistribution.map((range) => (
              <div className={styles.fact} key={range.range}>
                <dt>{range.range}</dt>
                <dd>{range.count}개</dd>
              </div>
            ))}
          </dl>
        </Card>
      )}

      <Card as="section" level={2}>
        <h3>역대 1등 이력</h3>
        {analysis.wonFirstPrize ? (
          <>
            <p>이 조합은 과거에 1등으로 당첨된 적이 있습니다.</p>
            <PrizeTable records={analysis.firstPrizeHistory} />
            <p className="note">
              이미 나온 조합이라고 해서 다시 나오지 않는 것은 아닙니다. 추첨은 이전 결과를 기억하지
              않습니다.
            </p>
          </>
        ) : (
          <p>이 조합은 아직 1등으로 나온 적이 없습니다.</p>
        )}
      </Card>
    </div>
  );
}
