package com.kraft.recommend.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BalancedScorerTest {

    @Test
    @DisplayName("다섯 조건을 모두 충족하면 5점(03문서 예시 [3,12,19,28,34,43])")
    void allFiveConditionsSatisfied_scoresFive() {
        BalancedScorer.Evaluation evaluation = BalancedScorer.evaluate(List.of(3, 12, 19, 28, 34, 43));

        assertThat(evaluation.score()).isEqualTo(5);
        assertThat(evaluation.codes()).containsExactlyInAnyOrder(
                ExplanationCode.ODD_EVEN_BALANCED, ExplanationCode.LOW_HIGH_BALANCED,
                ExplanationCode.SUM_IN_RANGE, ExplanationCode.CONSECUTIVE_PAIR_LIMITED,
                ExplanationCode.DECADE_SPREAD);
    }

    @Test
    @DisplayName("홀수 2개는 경계 포함으로 통과한다")
    void oddCountTwo_isBoundaryIncluded() {
        // 2,4,6,8,10,3 -> odd={3} count=1... need exactly 2 odds
        List<Integer> numbers = List.of(2, 4, 6, 8, 9, 21); // odds: 9,21 = 2
        assertThat(BalancedScorer.evaluate(numbers).codes()).contains(ExplanationCode.ODD_EVEN_BALANCED);
    }

    @Test
    @DisplayName("홀수 1개는 범위 밖이라 점수를 받지 못한다")
    void oddCountOne_isOutOfBoundary() {
        List<Integer> numbers = List.of(2, 4, 6, 8, 10, 9); // odds: 9 only = 1
        assertThat(BalancedScorer.evaluate(numbers).codes()).doesNotContain(ExplanationCode.ODD_EVEN_BALANCED);
    }

    @Test
    @DisplayName("홀수 4개는 경계 포함으로 통과하고 5개는 통과하지 못한다")
    void oddCountFourAndFive_boundary() {
        List<Integer> four = List.of(1, 3, 5, 7, 2, 4); // odds: 1,3,5,7 = 4
        List<Integer> five = List.of(1, 3, 5, 7, 9, 2); // odds: 1,3,5,7,9 = 5
        assertThat(BalancedScorer.evaluate(four).codes()).contains(ExplanationCode.ODD_EVEN_BALANCED);
        assertThat(BalancedScorer.evaluate(five).codes()).doesNotContain(ExplanationCode.ODD_EVEN_BALANCED);
    }

    @Test
    @DisplayName("저수(1~22) 22는 포함, 23은 제외되어 고저 판정 경계가 정확하다")
    void lowHighBoundary_22vs23() {
        List<Integer> twoLow = List.of(22, 21, 30, 31, 40, 41); // low(<=22): 22,21 = 2
        List<Integer> oneLow = List.of(23, 24, 30, 31, 40, 41); // low(<=22): none from 23.. -> 0
        assertThat(BalancedScorer.evaluate(twoLow).codes()).contains(ExplanationCode.LOW_HIGH_BALANCED);
        assertThat(BalancedScorer.evaluate(oneLow).codes()).doesNotContain(ExplanationCode.LOW_HIGH_BALANCED);
    }

    @Test
    @DisplayName("합계 100/180은 경계 포함, 99/181은 범위 밖이다")
    void sumBoundaries() {
        // sum=100
        assertThat(BalancedScorer.evaluate(List.of(1, 2, 3, 4, 5, 85)).codes()).contains(ExplanationCode.SUM_IN_RANGE);
        // sum=99
        assertThat(BalancedScorer.evaluate(List.of(1, 2, 3, 4, 5, 84)).codes()).doesNotContain(ExplanationCode.SUM_IN_RANGE);
        // sum=180
        assertThat(BalancedScorer.evaluate(List.of(1, 2, 3, 4, 5, 165)).codes()).contains(ExplanationCode.SUM_IN_RANGE);
        // sum=181
        assertThat(BalancedScorer.evaluate(List.of(1, 2, 3, 4, 5, 166)).codes()).doesNotContain(ExplanationCode.SUM_IN_RANGE);
    }

    @Test
    @DisplayName("연속 쌍은 1개까지 통과하고 2개부터는 통과하지 못한다")
    void consecutivePairBoundary() {
        List<Integer> onePair = List.of(1, 2, 10, 20, 30, 40); // pair: (1,2) = 1
        List<Integer> twoPairs = List.of(1, 2, 3, 20, 30, 40); // pairs: (1,2),(2,3) = 2
        assertThat(BalancedScorer.evaluate(onePair).codes()).contains(ExplanationCode.CONSECUTIVE_PAIR_LIMITED);
        assertThat(BalancedScorer.evaluate(twoPairs).codes()).doesNotContain(ExplanationCode.CONSECUTIVE_PAIR_LIMITED);
    }

    @Test
    @DisplayName("구간 분산은 4구간 이상이면 통과하고 3구간이면 통과하지 못한다")
    void decadeSpreadBoundary() {
        List<Integer> fourDecades = List.of(5, 15, 25, 35, 6, 16); // decades used: 1,2,3,4 = 4
        List<Integer> threeDecades = List.of(5, 6, 15, 16, 25, 26); // decades used: 1,2,3 = 3
        assertThat(BalancedScorer.evaluate(fourDecades).codes()).contains(ExplanationCode.DECADE_SPREAD);
        assertThat(BalancedScorer.evaluate(threeDecades).codes()).doesNotContain(ExplanationCode.DECADE_SPREAD);
    }
}
