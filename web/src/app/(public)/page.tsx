import type { Metadata } from "next";
import { headers } from "next/headers";
import Link from "next/link";

import { getLatestRound } from "@/entities/round/api";
import { LottoBallSet } from "@/entities/round/ui/lotto-ball";
import { NONCE_HEADER } from "@/shared/config/csp";
import { publicEnv } from "@/shared/config/env";
import { ROUTES } from "@/shared/config/routes";
import { formatDrawDate, formatPrize } from "@/shared/lib/format";
import { JsonLd } from "@/shared/ui/json-ld";
import { Card } from "@/shared/ui/surface";

import styles from "./home.module.css";

export const metadata: Metadata = {
  title: "로또 당첨번호와 번호 추천",
  description:
    "최신 회차 당첨번호를 확인하고, 과거 데이터 기반 통계로 나만의 번호 조합을 만들어 보세요.",
  alternates: { canonical: "/" },
};

/**
 * 홈 — improvement_fe.md §23.1
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

      <section className={`${styles.hero} prose`} aria-labelledby="latest-round">
        <h1 id="latest-round">
          <span className={styles.roundLabel}>
            <span className={styles.roundNumber}>{latest.round}회</span>
            <span className={styles.drawDate}>{formatDrawDate(latest.drawDate)} 추첨</span>
          </span>
        </h1>

        <LottoBallSet numbers={latest.numbers} bonusNumber={latest.bonusNumber} size="lg" />

        <div className={styles.prizeRow}>
          <p>
            <span className={styles.prizeLabel}>1등 당첨금</span>
            <span className={styles.prizeValue}>{formatPrize(latest.firstPrizeAmount)}</span>
          </p>
          <p>
            <span className={styles.prizeLabel}>2등 당첨금</span>
            <span className={styles.prizeValue}>{formatPrize(latest.secondPrize)}</span>
          </p>
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

      <Card as="section" level={2}>
        <h2>이 서비스가 하는 일</h2>
        <p>
          동행복권이 공개한 역대 당첨 데이터를 모아 번호별 출현 빈도, 홀짝·고저·합계 패턴, 함께 나온
          번호를 계산합니다. 모든 수치는 과거 기록의 요약이며 다음 회차를 예측하지 않습니다.
        </p>
        <p>
          로또는 매 회차 독립적인 무작위 추첨이고 1등 당첨 확률은 8,145,060분의 1입니다. 통계는
          번호를 고르는 재미를 위한 참고 자료로만 사용하세요.
        </p>
      </Card>
    </div>
  );
}
