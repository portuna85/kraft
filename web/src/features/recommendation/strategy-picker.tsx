import { SegmentedControl } from "@/ui/primitives/segmented-control";
import type { Strategy } from "./types";

const OPTIONS: readonly { value: Strategy; label: string }[] = [
  { value: "random", label: "무작위" },
  { value: "balanced", label: "균형 조합" },
  { value: "reduce_shared_winner_risk", label: "공동 당첨 위험 완화" },
];

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
