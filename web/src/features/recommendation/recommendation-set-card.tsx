import { LottoBalls } from "@/ui/domain/lotto-balls";
import { Badge } from "@/ui/primitives/badge";
import { explanationLabel } from "./explanation-codes";
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
  return (
    <article className="recommend-card">
      <div className="recommend-card-row">
        <div className="recommend-card-info">
          <p className="eyebrow">추천 {item.position}</p>
          <LottoBalls numbers={item.numbers} />
          {item.explanationCodes.length > 0 ? (
            <div className="recommend-card-explanations">
              {item.explanationCodes.map((code) => (
                <Badge key={code} tone="brand">
                  {explanationLabel(code)}
                </Badge>
              ))}
            </div>
          ) : null}
        </div>
        <button
          type="button"
          onClick={onSave}
          disabled={isSaving || isSaved}
          className={`recommend-save-btn${isSaved ? " saved" : ""}`}
        >
          {isSaved ? "저장됨" : isSaving ? "저장 중..." : "저장"}
        </button>
      </div>
    </article>
  );
}
