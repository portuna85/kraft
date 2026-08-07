import { LottoBall } from "../../round/ui/lotto-ball";
import { expectedFrequency } from "../schema";

import styles from "./frequency-bar.module.css";

/** SVG 내부 좌표계. 실제 크기는 CSS가 정하고 여기서는 비율만 다룬다. */
const VIEWBOX_WIDTH = 100;

/**
 * 번호별 출현 막대 — improvement_fe.md §9.4, §6.3 M-6
 *
 * **기대값 마커가 이 컴포넌트의 존재 이유다.** 막대 길이만 보여주면 "17번이 152번
 * 나왔다"는 사실은 전달되지만 그게 많은 건지 적은 건지는 알 수 없다. 무작위 기대값
 * 선을 함께 그려야 숫자가 해석이 된다.
 *
 * 막대는 장식이고 값은 텍스트로도 읽힌다 — 시각 요소만으로 정보를 전달하지 않는다.
 */
export function FrequencyBar({
  ballNumber,
  frequency,
  maxFrequency,
  totalRounds,
}: {
  ballNumber: number;
  frequency: number;
  maxFrequency: number;
  totalRounds: number;
}) {
  const scale = maxFrequency === 0 ? 0 : VIEWBOX_WIDTH / maxFrequency;
  const fillWidth = Math.max(frequency * scale, 0);
  const baselineX = expectedFrequency(totalRounds) * scale;

  return (
    <li className={styles.row}>
      <span className={styles.number}>
        <LottoBall value={ballNumber} size="sm" />
      </span>

      <svg
        className={styles.chart}
        viewBox={`0 0 ${VIEWBOX_WIDTH} 12`}
        preserveAspectRatio="none"
        aria-hidden="true"
      >
        <rect className={styles.track} x="0" y="0" width={VIEWBOX_WIDTH} height="12" rx="6" />
        <rect className={styles.fill} x="0" y="0" width={fillWidth} height="12" rx="6" />
        <line className={styles.baseline} x1={baselineX} x2={baselineX} y1="-2" y2="14" />
      </svg>

      <span className={styles.value}>{frequency}회</span>
    </li>
  );
}

/** 마커가 무엇인지 말해 주지 않으면 선 하나는 아무 의미가 없다. */
export function FrequencyLegend({ totalRounds }: { totalRounds: number }) {
  return (
    <p className={styles.legend}>
      <span>
        세로선은 무작위 기대값 {expectedFrequency(totalRounds).toFixed(1)}회입니다 — 회차마다 45개
        중 6개가 뽑히므로 번호 하나가 이만큼 나오는 것이 평균입니다.
      </span>
    </p>
  );
}
