package com.kraft.recommend;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("형태 균형 점수기(BalancedScorer) 단위 테스트")
class BalancedScorerTest {

    private final BalancedScorer scorer = new BalancedScorer();

    @Test
    @DisplayName("홀짝·구간·합계·연속쌍·구간분포 조건을 모두 만족하면 만점(5점)과 모든 코드를 반환한다")
    void evaluate_perfectlyBalancedCombo_returnsFullScoreAndAllCodes() {
        // 홀짝 3:3, 저/고(1~22 vs 23~45) 3:3, 합계 132, 연속쌍 0, 5개 구간 모두 사용
        List<Integer> combo = List.of(3, 14, 22, 28, 35, 41);

        BalancedEvaluation evaluation = scorer.evaluate(combo);

        assertThat(evaluation.score()).isEqualTo(5);
        assertThat(evaluation.explanationCodes()).containsExactlyInAnyOrder(
                ExplanationCode.ODD_EVEN_BALANCED,
                ExplanationCode.LOW_HIGH_BALANCED,
                ExplanationCode.SUM_IN_RANGE,
                ExplanationCode.CONSECUTIVE_PAIR_LIMITED,
                ExplanationCode.DECADE_SPREAD
        );
    }

    @Test
    @DisplayName("모든 번호가 홀수·저구간·저합계·연속·한 구간에 몰리면 조건을 만족하지 못한다")
    void evaluate_skewedCombo_failsMostConditions() {
        // 전부 홀수, 전부 1~22, 합계 21(100 미만), 연속쌍 4개, 구간 1개(1~10 반, 11~20 반... 실제 1구간)
        List<Integer> combo = List.of(1, 3, 5, 7, 9, 11);

        BalancedEvaluation evaluation = scorer.evaluate(combo);

        assertThat(evaluation.explanationCodes()).doesNotContain(
                ExplanationCode.ODD_EVEN_BALANCED,
                ExplanationCode.LOW_HIGH_BALANCED,
                ExplanationCode.SUM_IN_RANGE
        );
    }

    @Test
    @DisplayName("연속 번호 쌍이 2개 이상이면 CONSECUTIVE_PAIR_LIMITED 조건을 만족하지 못한다")
    void evaluate_tooManyConsecutivePairs_missingCode() {
        List<Integer> combo = List.of(10, 11, 12, 20, 30, 40);

        BalancedEvaluation evaluation = scorer.evaluate(combo);

        assertThat(evaluation.explanationCodes()).doesNotContain(ExplanationCode.CONSECUTIVE_PAIR_LIMITED);
    }
}
