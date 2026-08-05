import { LottoBalls } from "@/ui/domain/lotto-balls";
import { Badge } from "@/ui/primitives/badge";
import { explanationLabel } from "./explanation-codes";
import { computeSetStats, formatSetStatsSummary } from "./set-stats";
import type { RecommendationItem } from "./types";

export function RecommendationSetCard({
  item,
  isSaving,
  isSaved,
  onSave,
}: {
  item: RecommendationItem;
  isSaving: boolean;
  isSaved: boolean;
  onSave: () => void;
}) {
  const summary = formatSetStatsSummary(computeSetStats(item.numbers));
  const saveLabel = isSaved ? "저장됨" : isSaving ? "저장 중..." : "저장";

  return (
    <article className="recommend-card">
      <div className="recommend-card-header">
        <p className="eyebrow">추천 {item.position}</p>
        <button
          type="button"
          onClick={onSave}
          disabled={isSaving || isSaved}
          aria-label={`추천 ${item.position} 조합 ${saveLabel}`}
          className={`recommend-save-btn${isSaved ? " saved" : ""}`}
        >
          {saveLabel}
        </button>
      </div>
      <LottoBalls numbers={item.numbers} />
      <p className="recommend-card-summary muted">{summary}</p>
      {item.explanationCodes.length > 0 ? (
        <details className="recommend-card-details">
          <summary>분석 근거 보기</summary>
          <div className="recommend-card-explanations">
            {item.explanationCodes.map((code) => (
              <Badge key={code} tone="brand">
                {explanationLabel(code)}
              </Badge>
            ))}
          </div>
        </details>
      ) : null}
    </article>
  );
}
