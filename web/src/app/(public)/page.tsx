import type { Metadata } from "next";
import { headers } from "next/headers";
import Link from "next/link";

import { getLatestRound } from "@/entities/round/api";
import { LottoBallSet } from "@/entities/round/ui/lotto-ball";
import { NONCE_HEADER } from "@/shared/config/csp";
import { publicEnv } from "@/shared/config/env";
import { ROUTES } from "@/shared/config/routes";
import { formatDrawDate, formatWon } from "@/shared/lib/format";
import { calculateNetPrize } from "@/shared/lib/prize-tax";
import { JsonLd } from "@/shared/ui/json-ld";
import { Card } from "@/shared/ui/surface";

import styles from "./home.module.css";

export const metadata: Metadata = {
  title: "최신회차",
  description:
    "최신 회차 당첨번호를 확인하고, 과거 데이터 기반 통계로 나만의 번호 조합을 만들어 보세요.",
  alternates: { canonical: "/" },
};

/**
 * 홈
 *
 * 이 화면의 LCP 요소는 당첨번호다. 그래서 클라이언트 컴포넌트를 두지 않고 RSC로만
 * 그린다 — 번호가 HTML에 이미 들어 있어야 검색 유입과 LCP가 함께 산다(§8.1).
 *
 * **최신 회차 조회 실패를 여기서 잡지 않는다.** 잡아서 폴백을 200으로 내보내면
 * 업타임 체커와 크롤러가 장애를 못 보고 Caddy가 그 상태를 캐시한다. 그대로 던져
 * (public) 셸의 error 경계가 받게 두는 것이 의도다(§6.5).
 */
export default async function HomePage() {
  const latest = await getLatestRound();
  const nonce = (await headers()).get(NONCE_HEADER) ?? undefined;

  return (
    <div className="stack">
      <JsonLd
        nonce={nonce}
        data={{
          "@context": "https://schema.org",
          "@type": "WebSite",
          name: "KRAFT Lotto",
          url: publicEnv.baseUrl,
        }}
      />

      {/* I-31: 히어로 카드는 prose 폭(~630px)에 좌측 정렬인데 바로 아래 안내
          카드는 셸 전체 폭(~1145px)까지 늘어나 데스크톱에서 불균형해 보였다 —
          /analysis처럼 좁은 단(히어로+행동)과 넓은 단(안내)을 나란히 둔다. */}
      <div className={styles.layout}>
        <div className="stack">
          <section className={`${styles.hero} prose`} aria-labelledby="latest-round">
            <h1 id="latest-round">
              {/* I-32: h1 전체가 회차 숫자뿐이라 색인되는 유일한 제목이 서비스도
                  목적도 말하지 않고 매주 바뀌었다 — 서술형 문구를 앞에 두고
                  회차는 부제 역할로 남긴다.
                  scripts/deploy/smoke-test.sh가 data-testid="latest-round" 바로
                  다음의 <strong>회차 숫자를 배포 게이트로 읽는다 — 문구·클래스가
                  바뀌어도 이 훅과 구조(다음 형제가 아니라 첫 자식으로 <strong>)는
                  유지한다. */}
              <span className={styles.heading}>최신회차</span>
              <span className={styles.roundLabel} data-testid="latest-round">
                <strong className={styles.roundNumber}>{latest.round}회</strong>
                <span className={styles.drawDate}>{formatDrawDate(latest.drawDate)} 추첨</span>
              </span>
            </h1>

            <LottoBallSet numbers={latest.numbers} bonusNumber={latest.bonusNumber} size="lg" />

            <div className={styles.prizeRow}>
              <div className={styles.prizeGroup}>
                <span className={styles.prizeLabel}>1등 당첨금</span>
                <span className={styles.prizeValue}>{formatWon(latest.firstPrizeAmount)}</span>
                <span className={styles.prizeNet}>
                  실 수령액 {formatWon(calculateNetPrize(latest.firstPrizeAmount))}
                </span>
              </div>
              <div className={styles.prizeGroup}>
                <span className={styles.prizeLabel}>2등 당첨금</span>
                <span className={styles.prizeValue}>{formatWon(latest.secondPrize)}</span>
                <span className={styles.prizeNet}>
                  실 수령액 {formatWon(calculateNetPrize(latest.secondPrize))}
                </span>
              </div>
            </div>
          </section>

          <nav className={styles.actions} aria-label="주요 기능">
            <Link className={styles.cta} href={ROUTES.recommend}>
              번호 추천받기
            </Link>
            <Link className={styles.secondaryCta} href={ROUTES.frequency}>
              번호별 출현 통계
            </Link>
          </nav>
        </div>

        <Card as="section" level={2}>
          <h2>이 서비스가 하는 일</h2>
          <p>
            동행복권이 공개한 역대 당첨 데이터를 모아 번호별 출현 빈도, 홀짝·고저·합계 패턴, 함께
            나온 번호를 계산합니다. 모든 수치는 과거 기록의 요약이며 다음 회차를 예측하지 않습니다.
          </p>
          <p>
            로또는 매 회차 독립적인 무작위 추첨이고 1등 당첨 확률은 8,145,060분의 1입니다. 통계는
            번호를 고르는 재미를 위한 참고 자료로만 사용하세요.
          </p>
        </Card>
      </div>
    </div>
  );
}
