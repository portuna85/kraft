"use client";

import Link from "next/link";
import { NumberBoard } from "@/features/recommendation/number-board";
import { StrategyPicker } from "@/features/recommendation/strategy-picker";
import { RecommendationSetCard } from "@/features/recommendation/recommendation-set-card";
import { ProbabilityDisclaimer } from "@/features/recommendation/disclaimer";
import { useRecommendationStudio } from "@/features/recommendation/use-recommendation-studio";
import { MAX_COUNT, MIN_COUNT } from "@/features/recommendation/types";
import { Button } from "@/ui/primitives/button";
import { EmptyState } from "@/ui/primitives/empty-state";
import styles from "./recommendation-studio.module.css";

const STRATEGY_LABELS = {
  random: "무작위",
  balanced: "균형 조합",
  reduce_shared_winner_risk: "공동 당첨 위험 완화",
} as const;

export function RecommendClient() {
  const studio = useRecommendationStudio();

  return (
    <div className={`${styles.studio} recommend-layout`}>
      <form
        className={`${styles.controlPanel} recommend-form`}
        onSubmit={(event) => {
          event.preventDefault();
          void studio.generate();
        }}
      >
        <div className={styles.panelHeader}>
          <p className={styles.eyebrow}>Strategy &amp; numbers</p>
          <h3 className={styles.title}>조합 조건 설정</h3>
          <p className={styles.description}>번호를 누르면 고정, 다시 누르면 제외, 한 번 더 누르면 해제됩니다.</p>
        </div>

        <StrategyPicker value={studio.strategy} onChange={studio.setStrategy} />

        <NumberBoard
          locked={studio.locked}
          excluded={studio.excluded}
          onChange={studio.setLockedAndExcluded}
        />

        <label className={styles.countField}>
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

        <div className={styles.submitRow}>
          <Button variant="primary" size="lg" type="submit" loading={studio.isPending} loadingLabel="추천 번호 생성 중">
            추천 생성
          </Button>
        </div>

        <ProbabilityDisclaimer />
        <p className={`${styles.links} muted`}>
          <Link href="/info/faq">자세히 보기</Link>
          {" · "}
          <Link href="/recommend/history">저장한 추천 이력 보기</Link>
        </p>
      </form>

      <section className={styles.resultPanel} aria-labelledby="recommend-preview-title" aria-busy={studio.isPending}>
        <div className={styles.panelHeader}>
          <p className={styles.eyebrow}>Generated sets</p>
          <h3 id="recommend-preview-title" className={styles.title}>추천 결과</h3>
          <p className={styles.description}>생성된 조합은 실제 추천 API 결과이며 원하는 조합만 보관할 수 있습니다.</p>
        </div>

        {studio.message ? <p className={`${styles.status} status-text`} role="status" aria-live="polite">{studio.message}</p> : null}
        {studio.meta ? (
          <p className={`${styles.meta} muted recommend-set-meta`}>
            전략: {STRATEGY_LABELS[studio.meta.strategy]} · 알고리즘 버전: {studio.meta.algorithmVersion} · 반영 회차: {studio.meta.historyThroughRound}회
            {studio.meta.historicalExclusionApplied ? " · 역대 1등 조합 제외 적용" : ""}
            {studio.meta.exclusionPolicyVersion ? ` · 정책: ${studio.meta.exclusionPolicyVersion}` : ""}
          </p>
        ) : null}

        {studio.items.length > 0 ? (
          <div className={`${styles.results} recommend-grid`}>
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
        ) : (
          <div className={styles.empty}>
            <EmptyState title={studio.isPending ? "번호 조합을 만들고 있습니다" : "아직 생성된 조합이 없습니다"} description={studio.isPending ? "조건을 확인하고 과거 당첨 이력과 대조하는 중입니다." : "조건을 선택한 뒤 추천 생성 버튼을 눌러 주세요."} />
          </div>
        )}
      </section>
    </div>
  );
}
