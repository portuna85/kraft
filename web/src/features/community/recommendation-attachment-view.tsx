import { LottoBalls } from "@/ui/domain/lotto-balls";
import type { RecommendationAttachment } from "@/lib/community-api";

const STRATEGY_LABELS: Record<string, string> = {
  random: "무작위",
  balanced: "균형 조합",
  reduce_shared_winner_risk: "공동 당첨 위험 완화",
};

export function RecommendationAttachmentView({ attachment }: { attachment: RecommendationAttachment }) {
  return (
    <section className="community-recommendation-attachment">
      <p className="muted">
        {STRATEGY_LABELS[attachment.strategy] ?? attachment.strategy} · {attachment.algorithmVersion} · 반영 회차{" "}
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
