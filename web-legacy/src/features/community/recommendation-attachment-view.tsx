import { LottoBalls } from "@/ui/domain/lotto-balls";
import type { RecommendationAttachment } from "@/lib/community-api";
import { STRATEGY_LABELS, type Strategy } from "@/lib/domain/recommendation";

function strategyLabel(strategy: string): string {
  return strategy in STRATEGY_LABELS ? STRATEGY_LABELS[strategy as Strategy] : strategy;
}

export function RecommendationAttachmentView({ attachment }: { attachment: RecommendationAttachment }) {
  return (
    <section className="community-recommendation-attachment">
      <p className="muted">
        {strategyLabel(attachment.strategy)} · {attachment.algorithmVersion} · 반영 회차{" "}
        {attachment.historyThroughRound}회 · 정책: {attachment.exclusionPolicyVersion}
      </p>
      {attachment.items.map((item) => (
        <div key={item.position}>
          <LottoBalls numbers={item.numbers} />
        </div>
      ))}
    </section>
  );
}
