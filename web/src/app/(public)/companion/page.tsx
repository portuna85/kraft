import type { Metadata } from "next";
import Link from "next/link";

import { getCompanionStats } from "@/entities/statistics/api";
import { expectedCoOccurrence, TOTAL_PAIR_COUNT } from "@/entities/statistics/schema";
import { CompanionPairRow } from "@/entities/statistics/ui/companion-pair-row";
import { ROUTES } from "@/shared/config/routes";
import { EmptyState } from "@/shared/ui/states";
import { Table } from "@/shared/ui/surface";

import styles from "./companion.module.css";

export const metadata: Metadata = {
  title: "함께 나온 번호",
  description:
    "로또 6/45 역대 당첨 번호에서 어떤 번호 쌍이 자주 함께 나왔는지, 쌍당 평균과 비교해 보여줍니다.",
  alternates: { canonical: "/companion" },
};

/**
 * 초기 표시 개수 — improvement_fe.md §23.5 불변식
 *
 * 백엔드는 990쌍을 전부 보낸다. 그걸 그대로 렌더하면 SSR HTML이 990행이 되어 LCP를
 * 망친다. 필터를 걸었을 때는 그 번호가 낀 쌍이 최대 44개뿐이라 자르지 않는다.
 */
const TOP_PAIR_LIMIT = 50;

/** 1~45 밖이면 백엔드가 400 INVALID_BALL을 낸다. 화면에서 먼저 거른다. */
function parseBall(raw: string | undefined): number | undefined {
  if (raw === undefined) return undefined;
  const parsed = Number(raw);
  if (!Number.isInteger(parsed) || parsed < 1 || parsed > 45) return undefined;
  return parsed;
}

export default async function CompanionPage({
  searchParams,
}: {
  searchParams: Promise<{ ball?: string }>;
}) {
  const { ball: rawBall } = await searchParams;
  const ball = parseBall(rawBall);
  const stats = await getCompanionStats(ball);

  const expected = expectedCoOccurrence(stats.totalRounds);
  const pairs = ball === undefined ? stats.topPairs.slice(0, TOP_PAIR_LIMIT) : stats.topPairs;

  return (
    <div className="stack">
      <header className="prose stack">
        <h1>함께 나온 번호</h1>
        <p>
          집계 대상 {stats.totalRounds}회차 기준입니다. 45개 번호로 만들 수 있는 쌍은{" "}
          {TOTAL_PAIR_COUNT}개이고, 회차마다 6개에서 15개의 쌍이 생기므로 쌍 하나의 평균 동반 출현은{" "}
          {expected.toFixed(1)}회입니다.
        </p>
        <p>
          평균보다 많이 나온 쌍이 다음에도 함께 나올 확률이 높지는 않습니다. 990개 쌍이 있으면 그중
          일부가 평균을 웃도는 것은 무작위 추첨에서도 반드시 생기는 일입니다.
        </p>
      </header>

      <section aria-labelledby="ball-filter" className="stack">
        <h2 id="ball-filter">번호로 좁혀 보기</h2>
        <p className={styles.note}>
          번호를 고르면 그 번호가 포함된 쌍 전체를 서버에서 다시 조회합니다 — 상위 50위 밖의 쌍도
          빠짐없이 나옵니다.
        </p>
        <nav className={styles.balls} aria-label="번호 필터">
          <Link
            className={styles.ball}
            href={ROUTES.companion}
            aria-current={ball === undefined ? "page" : undefined}
          >
            전체
          </Link>
          {Array.from({ length: 45 }, (_, index) => index + 1).map((value) => (
            <Link
              key={value}
              className={styles.ball}
              href={`${ROUTES.companion}?ball=${value}`}
              aria-current={value === ball ? "page" : undefined}
            >
              {value}
            </Link>
          ))}
        </nav>
      </section>

      <section aria-labelledby="pairs" className="stack">
        <h2 id="pairs">
          {ball === undefined ? `자주 함께 나온 상위 ${TOP_PAIR_LIMIT}쌍` : `${ball}번이 포함된 쌍`}
        </h2>
        <p className={styles.note} role="status">
          {pairs.length}개 쌍을 표시하고 있습니다. 배율은 쌍당 평균 {expected.toFixed(1)}회 대비
          값입니다.
        </p>

        {pairs.length === 0 ? (
          ball === undefined ? (
            <EmptyState
              reason="no-data"
              title="아직 집계된 쌍이 없습니다"
              description="회차 데이터가 쌓이면 동반 출현 통계가 표시됩니다."
            />
          ) : (
            <EmptyState
              reason="filtered"
              title={`${ball}번이 포함된 쌍이 없습니다`}
              description="이 번호는 아직 다른 번호와 함께 나온 기록이 없습니다."
              action={<Link href={ROUTES.companion}>전체 쌍 보기</Link>}
            />
          )
        ) : (
          <Table caption="번호 쌍별 동반 출현 횟수와 평균 대비 배율" captionVisible={false}>
            <thead>
              <tr>
                <th scope="col">번호 쌍</th>
                <th scope="col">함께 나온 횟수</th>
                <th scope="col">평균 대비</th>
              </tr>
            </thead>
            <tbody>
              {pairs.map((pair) => (
                <CompanionPairRow
                  key={`${pair.ballA}-${pair.ballB}`}
                  pair={pair}
                  totalRounds={stats.totalRounds}
                />
              ))}
            </tbody>
          </Table>
        )}
      </section>
    </div>
  );
}
