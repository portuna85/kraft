import type { Metadata } from "next";
import Link from "next/link";
import { LottoBalls } from "@/components/lotto-balls";
import { PrizeTable } from "@/components/prize-table";
import { DataFreshnessNote } from "@/components/data-freshness-note";
import { getLatestWinningNumber, getRoundFreshness, type RoundFreshness, type WinningNumber } from "@/lib/api";
import { formatDrawDate } from "@/lib/format";
import { logCoreDataFailure } from "@/lib/logger";

// 루트 레이아웃의 title.template("%s | KRAFT Lotto")은 "/" 페이지에는 적용되지 않으므로
// (검증됨: 다른 페이지는 템플릿이 적용되지만 홈은 적용 안 됨) 접미사를 직접 포함해야 한다.
export async function generateMetadata(): Promise<Metadata> {
  try {
    const latest = await getLatestWinningNumber();
    return {
      title: `${latest.round}회 로또 당첨번호 (${latest.drawDate}) | KRAFT Lotto`,
      description: `${latest.round}회 로또 6/45 당첨 번호 ${[...latest.numbers].join(", ")} 보너스 ${latest.bonusNumber}. 당첨 결과 조회와 번호 추천을 제공합니다.`,
      alternates: { canonical: "/" },
      openGraph: {
        title: `${latest.round}회 로또 당첨번호 | KRAFT Lotto`,
        description: `${latest.round}회 당첨 번호 ${[...latest.numbers, latest.bonusNumber].join(", ")}`,
        url: "/",
      },
    };
  } catch {
    return {
      title: "KRAFT Lotto | 로또 6/45 결과와 번호 추천",
      description: "로또 6/45 최신 당첨 번호 조회, 번호 추천, 통계 분석 기능을 제공합니다.",
      alternates: { canonical: "/" },
    };
  }
}

export default async function HomePage() {
  // 홈의 유일한 핵심 데이터 — 실패를 "준비 중입니다" 200으로 숨기면 업타임 체커·크롤러가
  // 장애를 인지하지 못하고 Caddy가 그 상태를 60초간 캐시한다. 여기서는 로그만 남기고
  // 에러를 그대로 던져 error.tsx(5xx)가 처리하게 한다.
  let latest: WinningNumber;
  let freshness: RoundFreshness | null;
  try {
    [latest, freshness] = await Promise.all([
      getLatestWinningNumber(),
      getRoundFreshness().catch(() => null),
    ]);
  } catch (error) {
    logCoreDataFailure(error, "최신 당첨번호 조회 실패 — 핵심 데이터 실패로 페이지 오류 처리");
    throw error;
  }

  return (
    <div className="section-stack">
      <section className="panel result-panel hero-panel">
        <p className="eyebrow">최신 결과</p>
        <h1 className="result-title">
          {latest.round}회 당첨 결과 <span className="result-date">({formatDrawDate(latest.drawDate)})</span>
        </h1>
        <LottoBalls numbers={latest.numbers} bonusNumber={latest.bonusNumber} />
        <PrizeTable firstPrizeAmount={latest.firstPrizeAmount} secondPrize={latest.secondPrize} />
        <DataFreshnessNote freshness={freshness} />
      </section>

      <section className="grid grid-3 home-shortcuts">
        <Link href="/recommend" className="stat-card stat-link">
          <p className="eyebrow">번호 추천</p>
          <h3>추천 조합</h3>
          <span className="stat-link-cta">추천 받기</span>
        </Link>

        <Link href="/saved" className="stat-card stat-link">
          <p className="eyebrow">저장 번호</p>
          <h3>내 번호 보관함</h3>
          <span className="stat-link-cta">저장 목록 보기</span>
        </Link>

        <Link href="/frequency" className="stat-card stat-link">
          <p className="eyebrow">출현 통계</p>
          <h3>번호 통계</h3>
          <span className="stat-link-cta">통계 보기</span>
        </Link>
      </section>
    </div>
  );
}
