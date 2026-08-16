import { LottoBall } from "../../round/ui/lotto-ball";
import { coOccurrenceRatio, type CompanionPair } from "../schema";

import styles from "./companion-pair-row.module.css";

/**
 * 동반 출현 쌍 한 줄
 *
 * 배율이 이 줄의 핵심이다. "1번과 12번이 38회 함께 나왔다"만으로는 많은지 알 수 없고,
 * "평균의 1.4배"가 붙어야 읽힌다.
 *
 * 순위를 표시하지 않는 이유: 표의 행 번호와 순위를 둘 다 쓰면 "1. 1"처럼 이중 표기가
 * 되고(§23.5 불변식), 번호 필터를 걸면 순위가 전체 순위인지 필터 안 순위인지도
 * 모호해진다. 정렬 순서 자체가 순위를 말한다.
 */
const MEDAL_LABEL: Record<1 | 2 | 3, string> = {
  1: "금",
  2: "은",
  3: "동",
};

export function CompanionPairRow({
  pair,
  totalRounds,
  rank,
}: {
  pair: CompanionPair;
  totalRounds: number;
  /** 1부터 시작하는 순위. 1~3위만 메달 뱃지를 받는다. */
  rank?: number;
}) {
  const ratio = coOccurrenceRatio(pair.coCount, totalRounds);
  const medal = rank === 1 || rank === 2 || rank === 3 ? rank : undefined;

  return (
    <tr>
      <th scope="row">
        <span className={styles.pair}>
          {/* I-30: 메달 폭만큼 자리를 항상 잡아둔다 — 순위 밖 행은 이 뱃지가
              없어 공이 왼쪽으로 당겨져 열이 어긋나 보였다. 색과 "금/은/동"
              텍스트만으로는 몇 위인지 전달되지 않아 aria-label로 "N위"를 더한다. */}
          <span
            className={`${styles.medal} ${
              medal !== undefined ? (styles[`medal${medal}`] ?? "") : styles.medalHidden
            }`}
            aria-hidden={medal === undefined ? true : undefined}
            aria-label={medal !== undefined ? `${medal}위` : undefined}
          >
            {medal !== undefined ? MEDAL_LABEL[medal] : null}
          </span>
          <LottoBall value={pair.ballA} size="sm" />
          <LottoBall value={pair.ballB} size="sm" />
        </span>
      </th>
      <td>{pair.coCount}회</td>
      <td>
        {ratio === null ? (
          "—"
        ) : (
          <span className={styles.ratioChip}>{`x${ratio.toFixed(2)}배`}</span>
        )}
      </td>
    </tr>
  );
}
