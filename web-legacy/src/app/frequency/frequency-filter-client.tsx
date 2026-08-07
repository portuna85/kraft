"use client";

import { useState } from "react";
import { LottoBalls } from "@/ui/domain/lotto-balls";
import { FirstPrizeHistoryList } from "@/components/first-prize-history";
import type { BallFrequency, FrequencyStatsResponse, RankedCombination } from "@/lib/api";
import { ballColorClass } from "@/lib/ball-color";
import { browserFetch } from "@/lib/browser-api";
import { ErrorState } from "@/ui/primitives/error-state";
import styles from "./frequency.module.css";

const FILTERS = [
  { label: "전체", value: null },
  { label: "최근 100회", value: 100 },
  { label: "최근 200회", value: 200 },
  { label: "최근 500회", value: 500 },
] as const;

type Props = {
  initial: FrequencyStatsResponse;
};

function BallWithStats({ item, sampleSize }: { item: BallFrequency; sampleSize: number }) {
  const pct = sampleSize > 0 ? ((item.frequency / sampleSize) * 100).toFixed(1) : "0.0";

  return (
    <li className={`${styles.ballItem} ${styles.item}`} data-testid="frequency-item">
      <span className={`ball ball-sm ${ballColorClass(item.ballNumber)}`}>{item.ballNumber}</span>
      <span className={`${styles.count} tabular-nums`}>{item.frequency}회</span>
      <span className={`${styles.pct} tabular-nums`}>{pct}%</span>
    </li>
  );
}

function CombinationGroup({ label, combination }: { label: string; combination: RankedCombination }) {
  return (
    <div className={styles.rankGroup}>
      <p className={styles.rankLabel}>{label}</p>
      <LottoBalls numbers={combination.balls.map((item) => item.ballNumber)} />
      <FirstPrizeHistoryList
        history={combination.firstPrizeHistory}
        compact
        emptyMessage="자주 나온 번호를 모은 목록이며, 실제 당첨 조합이 아닙니다. 이 6개가 한 회차에 함께 나온 기록은 없습니다."
      />
    </div>
  );
}

export function FrequencyFilterClient({ initial }: Props) {
  const [stats, setStats] = useState(initial);
  const [activeLimit, setActiveLimit] = useState<number | null>(null);
  const [pendingLimit, setPendingLimit] = useState<number | null>(null);
  const [filterState, setFilterState] = useState<"idle" | "loading" | "error">("idle");

  function applyFilter(limit: number | null) {
    if (limit === activeLimit) return;

    if (limit === null) {
      setStats(initial);
      setActiveLimit(null);
      setFilterState("idle");
      return;
    }

    setPendingLimit(limit);
    setFilterState("loading");
    browserFetch<FrequencyStatsResponse>(`/api/v1/stats/frequency?limit=${limit}`)
      .then((response) => {
        setStats(response);
        setActiveLimit(limit);
        setFilterState("idle");
      })
      .catch(() => {
        // 이전 stats/activeLimit을 유지 — 실패했다는 사실만 알린다.
        setFilterState("error");
      });
  }

  function retry() {
    if (pendingLimit !== null) applyFilter(pendingLimit);
  }

  const byNumber = [...stats.frequencies].sort((a, b) => a.ballNumber - b.ballNumber);
  // 요청한 limit(activeLimit)이 아니라 백엔드가 실제로 집계한 표본 수(stats.totalRounds)를
  // 써야 한다 — limit이 실제 저장된 회차 수보다 크면 둘이 달라진다(T1).
  const sampleSize = stats.totalRounds;

  return (
    <>
      {/* 핵심 지표 요약을 필터·상세보다 먼저 배치 — /stats·/analysis와 동일한
          "요약 → 필터 → 상세" 순서(docs/improvement.md §13 Priority 1). */}
      <div className="freq-summary">
        <CombinationGroup label="가장 자주 나온 번호 TOP 6" combination={stats.topSix} />
        <CombinationGroup label="가장 적게 나온 번호 BOTTOM 6" combination={stats.bottomSix} />
      </div>

      {/* 탭 전환 시 별도 tabpanel이 아니라 같은 영역의 내용만 갱신되므로
          tablist/tab 대신 단순 토글 버튼 그룹(aria-pressed)으로 표현한다. */}
      <div className="freq-filter-tabs" role="group" aria-label="조회 기간">
        {FILTERS.map(({ label, value }) => (
          <button
            key={label}
            type="button"
            aria-pressed={activeLimit === value}
            disabled={filterState === "loading"}
            onClick={() => applyFilter(value)}
            className={`freq-filter-tab${activeLimit === value ? " active" : ""}`}
          >
            {label}
          </button>
        ))}
      </div>

      {/* FE-008: 설명문·진행 상태·오류·재시도가 한 문단에 섞여 오류의 위계가 낮았다.
          설명은 설명대로 두고 오류는 공통 ErrorState(inline)로 분리한다. */}
      <p className={styles.desc} aria-live="polite">
        {activeLimit === null ? `총 ${stats.totalRounds}회 전체 기준` : `최근 ${stats.totalRounds}회 기준`}으로 각 번호가
        당첨 번호에 포함된 누적 횟수를 보여줍니다.
        {filterState === "loading" && <span className="muted"> 불러오는 중...</span>}
      </p>
      {filterState === "error" && (
        <ErrorState
          variant="inline"
          title="기간별 통계를 불러오지 못했습니다."
          description="이전 결과를 그대로 보여주고 있습니다."
          retry={{ label: "다시 시도", onClick: retry }}
        />
      )}

      <ul className={styles.grid} data-testid="frequency-grid" aria-label="번호별 출현 통계">
        {byNumber.map((item) => (
          <BallWithStats key={item.ballNumber} item={item} sampleSize={sampleSize} />
        ))}
      </ul>
    </>
  );
}
