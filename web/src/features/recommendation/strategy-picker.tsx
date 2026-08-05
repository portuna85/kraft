import { SegmentedControl } from "@/ui/primitives/segmented-control";
import { STRATEGY_DESCRIPTIONS, STRATEGY_LABELS } from "@/lib/domain/recommendation";
import type { Strategy } from "./types";
import styles from "./strategy-picker.module.css";

// L-1: 라벨 문구를 여기서 다시 손으로 베끼지 않고 공유 맵에서 파생시킨다 — 키 순서가
// 그대로 화면 표시 순서가 된다(STRATEGY_LABELS 선언 순서와 동일).
const OPTIONS: readonly { value: Strategy; label: string }[] = (
  Object.entries(STRATEGY_LABELS) as [Strategy, string][]
).map(([value, label]) => ({ value, label }));

export function StrategyPicker({
  value,
  onChange,
}: {
  value: Strategy;
  onChange: (value: Strategy) => void;
}) {
  return (
    <div>
      <SegmentedControl<Strategy>
        options={OPTIONS}
        value={value}
        onChange={onChange}
        aria-label="추천 전략"
      />
      {/* 명칭만으로는 "균형 조합"·"공동 당첨 위험 완화"가 무엇을 하는지 알기
          어려워 전략별 1줄 설명을 병기한다. 문구는 /info/methodology와 동일한
          STRATEGY_DESCRIPTIONS에서 가져온다. */}
      <dl className={styles.descriptions}>
        {OPTIONS.map((option) => (
          <div key={option.value} className={styles.descriptionRow}>
            <dt>{option.label}</dt>
            <dd>{STRATEGY_DESCRIPTIONS[option.value]}</dd>
          </div>
        ))}
      </dl>
    </div>
  );
}
