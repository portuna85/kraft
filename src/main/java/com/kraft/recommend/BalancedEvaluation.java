package com.kraft.recommend;

import java.util.List;

/** {@link BalancedScorer}가 조합 하나를 평가한 결과. */
public record BalancedEvaluation(int score, List<ExplanationCode> explanationCodes) {
}
