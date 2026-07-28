"use client";

import Link from "next/link";
import { NumberBoard } from "@/features/recommendation/number-board";
import { StrategyPicker } from "@/features/recommendation/strategy-picker";
import { RecommendationSetCard } from "@/features/recommendation/recommendation-set-card";
import { ProbabilityDisclaimer } from "@/features/recommendation/disclaimer";
import { useRecommendationStudio } from "@/features/recommendation/use-recommendation-studio";
import { MAX_COUNT, MIN_COUNT } from "@/features/recommendation/types";

const STRATEGY_LABELS = {
  random: "무작위",
  balanced: "균형 조합",
  reduce_shared_winner_risk: "공동 당첨 위험 완화",
} as const;

export function RecommendClient() {
  const studio = useRecommendationStudio();

  return (
    <div className="recommend-layout">
      <form
        className="recommend-form"
        onSubmit={(event) => {
          event.preventDefault();
          void studio.generate();
        }}
      >
        <StrategyPicker value={studio.strategy} onChange={studio.setStrategy} />

        <NumberBoard
          locked={studio.locked}
          excluded={studio.excluded}
          onChange={studio.setLockedAndExcluded}
        />

        <label>
          조합 수
          <input
            type="number"
            inputMode="numeric"
            min={MIN_COUNT}
            max={MAX_COUNT}
            value={studio.count}
            onChange={(event) => studio.setCount(Number(event.target.value))}
          />
        </label>

        <button type="submit" disabled={studio.isPending}>
          {studio.isPending ? "생성 중..." : "추천 생성"}
        </button>

        <ProbabilityDisclaimer />
        <p className="muted">
          <Link href="/info/faq">자세히 보기</Link>
          {" · "}
          <Link href="/recommend/history">저장한 추천 이력 보기</Link>
        </p>
      </form>

      {studio.message ? (
        <p className="status-text" role="status" aria-live="polite">
          {studio.message}
        </p>
      ) : null}

      {studio.meta ? (
        <p className="muted recommend-set-meta">
          전략: {STRATEGY_LABELS[studio.meta.strategy]} · 알고리즘 버전: {studio.meta.algorithmVersion} · 반영 회차:{" "}
          {studio.meta.historyThroughRound}회
        </p>
      ) : null}

      {studio.items.length > 0 && (
        <div className="recommend-grid">
          {studio.items.map((item) => (
            <RecommendationSetCard
              key={`${item.position}-${item.numbers.join("-")}`}
              item={item}
              isSaving={studio.savingPositions.has(item.position)}
              isSaved={studio.savedPositions.has(item.position)}
              onSave={() => studio.save(item.numbers, item.position)}
            />
          ))}
        </div>
      )}
    </div>
  );
}
