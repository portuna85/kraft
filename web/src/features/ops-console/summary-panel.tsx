import type { OpsSummary } from "@/entities/ops/schema";
import { formatDateTime, formatDrawDate } from "@/shared/lib/format";
import { InlineAlert } from "@/shared/ui/states";
import { Stat } from "@/shared/ui/stat";

import styles from "./ops-console.module.css";

/**
 * 상태 요약 패널 — improvement_fe.md §23.14 ②.
 *
 * `summary`가 `null`이면 "아직 조회 안 함"이다 — 별도 에러 상태를 두지 않는다.
 * 조회 실패는 `OpsConsole`이 `InlineAlert`로 이미 보여주므로 여기서 중복하지 않는다.
 */
export function SummaryPanel({ summary }: { summary: OpsSummary | null }) {
  if (summary === null) {
    return <p className={styles.note}>토큰을 입력하고 상태를 확인해 주세요.</p>;
  }

  return (
    <div className="stack">
      {!summary.fresh && (
        <InlineAlert tone="danger" title="반영 지연">
          최신 회차가 아직 반영되지 않았습니다.
        </InlineAlert>
      )}
      <div className={styles.grid}>
        <Stat label="서비스" value={summary.service} />
        <Stat label="시간대" value={summary.timezone} />
        <Stat
          label="상태"
          value={summary.status}
          tone={summary.fresh ? "success" : "danger"}
        />
        <Stat
          label="최신 회차"
          value={summary.latestRound === null ? "—" : `${summary.latestRound}회`}
        />
        <Stat
          label="최신 추첨일"
          value={summary.latestDrawDate === null ? "—" : formatDrawDate(summary.latestDrawDate)}
        />
        <Stat label="확인 시각" value={formatDateTime(summary.checkedAt)} />
      </div>
    </div>
  );
}
