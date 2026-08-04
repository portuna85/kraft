import { SegmentedControl } from "@/ui/primitives/segmented-control";
import { STRATEGY_LABELS } from "@/lib/domain/recommendation";
import type { Strategy } from "./types";

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
    <SegmentedControl<Strategy>
      options={OPTIONS}
      value={value}
      onChange={onChange}
      aria-label="추천 전략"
    />
  );
}
