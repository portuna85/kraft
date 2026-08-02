import Link from "next/link";
import { DataFreshnessNote } from "@/components/data-freshness-note";
import { PrizeTable } from "@/components/prize-table";
import type { RoundFreshness, WinningNumber } from "@/lib/api";
import { formatDrawDate } from "@/lib/format";
import { serviceLinks } from "@/lib/home-service-links";
import { LottoBalls } from "@/ui/domain/lotto-balls";
import styles from "./home-sections.module.css";

/** 두 핵심 기능을 나란히 놓는 최상단 영역. 좁은 화면에서는 세로로 쌓인다. */
export function HomePrimary({ children }: { children: React.ReactNode }) {
  return <div className={styles.primary}>{children}</div>;
}

export function LatestResultSection({ latest, freshness }: { latest: WinningNumber; freshness: RoundFreshness | null }) {
  return (
    <section id="latest-result" className={styles.section} aria-labelledby="latest-result-title">
      <h1 id="latest-result-title" className={styles.sectionTitle}>이번 주 당첨결과</h1>
      <div className={`${styles.resultCard} result-panel hero-panel`}>
        <div className={styles.resultMain}>
          {/* 회차는 /frequency·/stats·/companion과의 정합성 스모크가 읽는 값이라
              제목 문구를 바꾸더라도 깨지지 않도록 data-testid로 고정한다. */}
          <p className={styles.round} data-testid="latest-round">
            <strong>{latest.round}회</strong>
            <span className={styles.drawDate}>{formatDrawDate(latest.drawDate)}</span>
          </p>
          <LottoBalls numbers={latest.numbers} bonusNumber={latest.bonusNumber} />
          <DataFreshnessNote freshness={freshness} />
        </div>
        <div className={styles.prizePanel}>
          <PrizeTable firstPrizeAmount={latest.firstPrizeAmount} secondPrize={latest.secondPrize} />
        </div>
      </div>
    </section>
  );
}

/**
 * 홈에서는 추천 스튜디오 전체(전략 선택 + 45칸 번호판 + 결과 목록)를 렌더링하지 않고
 * 진입 카드만 둔다. 홈을 짧게 유지하는 것이 목적이고, 부수적으로 홈에서 유일하게
 * 무거웠던 클라이언트 번들이 빠진다. 실제 기능은 /recommend가 그대로 소유한다.
 */
export function RecommendEntryCard() {
  return (
    <section className={styles.entryCard} aria-labelledby="home-recommend-title">
      <h2 id="home-recommend-title" className={styles.entryTitle}>나만의 번호 조합</h2>
      <p className={styles.entryLead}>고정·제외 번호를 정하고 전략을 골라 조합을 만듭니다.</p>
      <Link href="/recommend" className={styles.primaryAction}>번호 만들기</Link>
      <ul className={styles.entryLinks}>
        <li><Link href="/saved">보관함</Link></li>
        <li><Link href="/recommend/history">추천 이력</Link></li>
      </ul>
    </section>
  );
}

export function ServiceLinks() {
  return (
    <nav className={styles.services} aria-labelledby="home-services-title">
      <h2 id="home-services-title" className={styles.servicesTitle}>서비스 바로가기</h2>
      <ul className={styles.serviceGrid}>
        {serviceLinks.map((link) => (
          <li key={link.href}>
            <Link href={link.href} className={styles.serviceLink}>{link.label}</Link>
          </li>
        ))}
      </ul>
    </nav>
  );
}
