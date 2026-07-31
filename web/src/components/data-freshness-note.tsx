import type { RoundFreshness } from "@/lib/api";
import { formatDrawDate } from "@/lib/format";
import { StatusBadge } from "@/ui/primitives/status-badge";

export function DataFreshnessNote({ freshness }: { freshness: RoundFreshness | null }) {
  if (!freshness) return null;

  const label = freshness.fresh ? "최신 회차 반영 완료" : "최신 회차 반영 지연";

  return (
    <div className="data-freshness-note" role="status">
      <StatusBadge status={freshness.fresh ? "fresh" : "stale"} label={label} />
      <span className="muted">
        공식 데이터 기준 · {freshness.latestRound}회 ({formatDrawDate(freshness.latestDrawDate)})
        {freshness.fresh
          ? " · 최신 회차까지 반영됨"
          : " · 최신 추첨 결과 반영이 지연되고 있습니다. 잠시 후 다시 확인해 주세요."}
      </span>
    </div>
  );
}
