import type { RoundFreshness } from "@/lib/api";
import { formatDrawDate } from "@/lib/format";
import { StatusBadge } from "@/ui/primitives/status-badge";

export function DataFreshnessNote({ freshness }: { freshness: RoundFreshness | null }) {
  // FE-013: 최신성 조회는 실패를 허용하는 부가 데이터라 null이면 아무것도 렌더하지
  // 않았다. 그러면 "원래 최신성 표시가 없는 화면"과 "조회에 실패한 화면"이 같아 보인다.
  // 당첨 번호 자체는 정상이므로 오류로 키우지 않고, 확인 불가 사실만 조용히 알린다.
  if (!freshness) {
    return (
      <div className="data-freshness-note" role="status">
        <StatusBadge status="stale" label="최신성 확인 불가" />
        <span className="muted">
          공식 데이터 반영 상태를 불러오지 못했습니다. 표시된 회차 정보는 정상입니다.
        </span>
      </div>
    );
  }

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
