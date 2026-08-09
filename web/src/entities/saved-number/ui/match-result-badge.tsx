import { Badge } from "@/shared/ui/badge";

import { isWinning } from "../schema";

import styles from "./match-result-badge.module.css";

/**
 * 회차 대조 결과 — improvement_fe.md §9.4, §23.7
 *
 * **색만으로 등수를 전달하지 않는다.** Badge 계약이 label을 필수로 요구하는 이유이기도
 * 하고, 여기서는 몇 개 맞았는지·보너스가 맞았는지도 텍스트로 남긴다. 색각 이상이 있거나
 * 흑백으로 출력해도 결과를 읽을 수 있어야 한다.
 */
export function MatchResultBadge({
  prizeTier,
  matchedCount,
  bonusMatch,
}: {
  prizeTier: string;
  matchedCount: number;
  bonusMatch: boolean;
}) {
  return (
    <span className={styles.result}>
      <Badge tone={isWinning(prizeTier) ? "success" : "neutral"} label={prizeTier} />
      <span className={styles.detail}>
        {matchedCount}개 일치{bonusMatch && " · 보너스 일치"}
      </span>
    </span>
  );
}
