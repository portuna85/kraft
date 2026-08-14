import type { Metadata } from "next";
import Link from "next/link";

import {
  FREQUENCY_LIMITS,
  getFrequencyStats,
  type FrequencyLimit,
} from "@/entities/statistics/api";
import { expectedFrequency, frequencyRatio } from "@/entities/statistics/schema";
import { FrequencyBar, FrequencyLegend } from "@/entities/statistics/ui/frequency-bar";
import { LottoBallSet } from "@/entities/round/ui/lotto-ball";
import { publicEnv } from "@/shared/config/env";
import { ROUTES } from "@/shared/config/routes";
import { AdSenseSidebar, InArticleAd } from "@/shared/ui/ad-unit";
import { Card } from "@/shared/ui/surface";

import styles from "./frequency.module.css";

export const metadata: Metadata = {
  title: "번호별 출현 통계",
  description:
    "역대 당첨 회차에서 1~45번이 각각 몇 번 나왔는지, 무작위 기대값과 비교해 보여줍니다.",
  alternates: { canonical: "/frequency" },
};

/**
 * 기간 선택지 P-7
 *
 * 기간은 URL에 실리고 서버가 그 기간만 조회한다. 4개 기간을 한 문서에 담으면 페이로드가
 * 4배가 되고, 클라이언트 필터는 공유·뒤로가기도 못 한다.
 *
 * limit 값은 **백엔드가 허용하는 집합**이어야 한다 — StatisticsApiController의
 * ALLOWED_LIMITS는 {100, 200, 500}이고, 그 밖의 값은 400 INVALID_LIMIT이다. 여기에
 * 임의의 값(50·20 등)을 두면 그 선택지를 고르는 순간 페이지가 5xx로 떨어진다.
 */
const PERIODS: readonly { label: string; limit: FrequencyLimit | undefined }[] = [
  { label: "전체", limit: undefined },
  ...FREQUENCY_LIMITS.map((limit) => ({ label: `최근 ${limit}회`, limit })),
];

function parseLimit(raw: string | undefined): FrequencyLimit | undefined {
  return FREQUENCY_LIMITS.find((limit) => String(limit) === raw);
}

export default async function FrequencyPage({
  searchParams,
}: {
  searchParams: Promise<{ limit?: string }>;
}) {
  const { limit: rawLimit } = await searchParams;
  const limit = parseLimit(rawLimit);
  const stats = await getFrequencyStats(limit);

  const maxFrequency = stats.frequencies.reduce((max, item) => Math.max(max, item.frequency), 0);
  const expected = expectedFrequency(stats.totalRounds);
  const aboveExpected = stats.frequencies.filter((item) => item.frequency > expected).length;

  return (
    <div className="stack">
      <header className="prose stack">
        <h1>번호별 출현 통계</h1>
        <p>
          집계 대상 {stats.totalRounds}회차 기준입니다. 번호 하나의 무작위 기대 출현 횟수는{" "}
          {expected.toFixed(1)}회이고, 지금 기대값을 넘긴 번호는 {aboveExpected}개입니다.
        </p>
        <p>
          출현 횟수가 많다고 다음 회차에 더 나오지는 않습니다. 매 회차는 독립적인 무작위 추첨이며,
          이 표는 과거 기록의 요약일 뿐입니다.
        </p>
      </header>

      <nav className={styles.periods} aria-label="집계 기간">
        {PERIODS.map((period) => {
          const href =
            period.limit === undefined
              ? ROUTES.frequency
              : `${ROUTES.frequency}?limit=${period.limit}`;
          const isCurrent = period.limit === limit;
          return (
            <Link
              key={period.label}
              className={styles.period}
              href={href}
              aria-current={isCurrent ? "page" : undefined}
            >
              {period.label}
            </Link>
          );
        })}
      </nav>

      <section aria-labelledby="all-numbers" className="stack">
        <h2 id="all-numbers">1~45번 출현 횟수</h2>
        <FrequencyLegend totalRounds={stats.totalRounds} />
        <ol>
          {stats.frequencies.map((item) => (
            <FrequencyBar
              key={item.ballNumber}
              ballNumber={item.ballNumber}
              frequency={item.frequency}
              maxFrequency={maxFrequency}
              totalRounds={stats.totalRounds}
            />
          ))}
        </ol>
      </section>

      <div className={styles.extremes}>
        <Card as="section" level={2}>
          <h2>가장 많이 나온 6개</h2>
          <LottoBallSet numbers={stats.topSix.balls.map((ball) => ball.ballNumber)} />
          <p>
            {stats.topSix.wonFirstPrize
              ? "이 조합은 과거에 1등으로 당첨된 적이 있습니다."
              : "이 조합이 실제로 1등에 당첨된 적은 없습니다 — 자주 나온 번호를 모아도 그 조합이 나온다는 뜻은 아닙니다."}
          </p>
          <RatioNote balls={stats.topSix.balls} totalRounds={stats.totalRounds} />
        </Card>

        <Card as="section" level={2}>
          <h2>가장 적게 나온 6개</h2>
          <LottoBallSet numbers={stats.bottomSix.balls.map((ball) => ball.ballNumber)} />
          <p>
            적게 나온 번호가 &ldquo;나올 차례&rdquo;인 것은 아닙니다. 추첨은 이전 결과를 기억하지
            않습니다.
          </p>
          <RatioNote balls={stats.bottomSix.balls} totalRounds={stats.totalRounds} />
        </Card>
      </div>

      {/* web-legacy는 2단 사이드바 레이아웃 안에 이 슬롯을 두지만, web/의 페이지는
          단일 컬럼이라 본문 끝에 이어 둔다 — AdSenseSidebar 자체는 여전히 데스크톱
          뷰포트에서만 mount된다(뷰포트 게이트는 그대로 유지). */}
      <InArticleAd slot="frequency" />
      <AdSenseSidebar slot={publicEnv.adsenseUnitSidebar} />
    </div>
  );
}

/** 기대값 대비 배율을 문장으로 — 숫자만 두면 해석이 사용자 몫으로 남는다(M-6). */
function RatioNote({
  balls,
  totalRounds,
}: {
  balls: readonly { ballNumber: number; frequency: number }[];
  totalRounds: number;
}) {
  const first = balls[0];
  if (first === undefined) return null;

  const ratio = frequencyRatio(first.frequency, totalRounds);
  if (ratio === null) return null;

  return (
    <p className={styles.note}>
      예를 들어 {first.ballNumber}번은 {first.frequency}회 나왔고, 이는 기대값의 {ratio.toFixed(2)}
      배입니다.
    </p>
  );
}
