import { LottoBall } from "../../round/ui/lotto-ball";
import { coOccurrenceRatio, type CompanionPair } from "../schema";

import styles from "./companion-pair-row.module.css";

/**
 * 동반 출현 쌍 한 줄 — improvement_fe.md §9.4, §23.5
 *
 * 배율이 이 줄의 핵심이다. "1번과 12번이 38회 함께 나왔다"만으로는 많은지 알 수 없고,
 * "평균의 1.4배"가 붙어야 읽힌다.
 *
 * 순위를 표시하지 않는 이유: 표의 행 번호와 순위를 둘 다 쓰면 "1. 1"처럼 이중 표기가
 * 되고(§23.5 불변식), 번호 필터를 걸면 순위가 전체 순위인지 필터 안 순위인지도
 * 모호해진다. 정렬 순서 자체가 순위를 말한다.
 */
export function CompanionPairRow({
  pair,
  totalRounds,
}: {
  pair: CompanionPair;
  totalRounds: number;
}) {
  const ratio = coOccurrenceRatio(pair.coCount, totalRounds);

  return (
    <tr>
      <th scope="row">
        <span className={styles.pair}>
          <LottoBall value={pair.ballA} size="sm" />
          <LottoBall value={pair.ballB} size="sm" />
        </span>
      </th>
      <td>{pair.coCount}회</td>
      <td>{ratio === null ? "—" : `${ratio.toFixed(2)}배`}</td>
    </tr>
  );
}
