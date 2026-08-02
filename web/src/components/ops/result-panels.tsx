import { LottoBalls } from "@/ui/domain/lotto-balls";
import { formatCurrency, formatDateTime, formatDrawDate } from "@/lib/format";
import type { OpsSummary, WinningNumber } from "@/lib/ops-types";

/** FE-078: 순수 표시 전용 패널 — 상태를 갖지 않으므로 대시보드 본체에서 분리했다. */
export function SummaryPanel({ summary }: { summary: OpsSummary }) {
  return (
    <article className="panel">
      <p className="eyebrow">운영 상태</p>
      <h2 className="ops-title">{summary.service}</h2>
      <div className="ops-summary-grid">
        <div className="ops-summary-card">
          <strong>서비스 상태</strong>
          <span>{summary.status}</span>
        </div>
        <div className="ops-summary-card">
          <strong>시간대</strong>
          <span>{summary.timezone}</span>
        </div>
        <div className="ops-summary-card">
          <strong>최신 저장 회차</strong>
          <span>{summary.latestRound === null ? "없음" : `${summary.latestRound}회`}</span>
        </div>
        <div className="ops-summary-card">
          <strong>최신 추첨일</strong>
          <span>{summary.latestDrawDate ? formatDrawDate(summary.latestDrawDate) : "없음"}</span>
        </div>
        <div className="ops-summary-card">
          <strong>신선도</strong>
          <span>{summary.fresh ? "최신 상태" : "점검 필요"}</span>
        </div>
        <div className="ops-summary-card">
          <strong>확인 시각</strong>
          <span>{formatDateTime(summary.checkedAt)}</span>
        </div>
      </div>
    </article>
  );
}

export function CollectedPanel({ result }: { result: WinningNumber }) {
  return (
    <article className="panel">
      <p className="eyebrow">최근 반영 결과</p>
      <h2 className="ops-title">{result.round}회차</h2>
      <p className="page-subtitle">{formatDrawDate(result.drawDate)} 기준 반영 데이터</p>
      <LottoBalls numbers={result.numbers} bonusNumber={result.bonusNumber} />
      <p className="muted prize-line">1등 당첨금 {formatCurrency(result.firstPrizeAmount)}</p>
    </article>
  );
}
