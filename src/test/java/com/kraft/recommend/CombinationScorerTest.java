package com.kraft.recommend;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("조합 점수 계산기 단위 테스트")
class CombinationScorerTest {

    private CombinationScorer scorer;

    @BeforeEach
    void setUp() {
        scorer = new CombinationScorer();
    }

    // ── 비인기도 점수 방향성 ───────────────────────────────────────────────────

    @Test
    @DisplayName("고번호 조합은 저번호 조합보다 점수가 높다")
    void score_highNumberCombo_outscoresLowNumberCombo() {
        List<Integer> highNumbers  = List.of(32, 35, 38, 40, 43, 45); // 모두 32 이상
        List<Integer> lowNumbers   = List.of(1, 2, 3, 4, 5, 6);       // 한 자리 + 낮은 합계

        assertThat(scorer.score(highNumbers)).isGreaterThan(scorer.score(lowNumbers));
    }

    @Test
    @DisplayName("높은 합계 조합은 낮은 합계 조합보다 점수가 높다")
    void score_highSumCombo_outscoresLowSumCombo() {
        List<Integer> highSum = List.of(30, 32, 34, 36, 38, 40); // 합계 210
        List<Integer> lowSum  = List.of(1, 2, 3, 4, 5, 9);       // 합계 24

        assertThat(scorer.score(highSum)).isGreaterThan(scorer.score(lowSum));
    }

    // ── 개별 페널티 검증 ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("한 자리 숫자 페널티")
    class SingleDigitPenalty {

        @Test
        @DisplayName("1~9 번호 하나를 포함하면 그 자리를 32 이상으로 바꾼 조합보다 점수가 낮다")
        void singleDigit_lowersScore() {
            List<Integer> withSingleDigit    = List.of(3, 32, 34, 36, 38, 40);
            List<Integer> withoutSingleDigit = List.of(33, 32, 34, 36, 38, 40);

            assertThat(scorer.score(withSingleDigit)).isLessThan(scorer.score(withoutSingleDigit));
        }
    }

    @Nested
    @DisplayName("5 배수 페널티")
    class MultipleOf5Penalty {

        @Test
        @DisplayName("5 배수 번호 하나를 포함하면 비배수로 바꾼 조합보다 점수가 낮다")
        void multipleOf5_lowersScore() {
            List<Integer> withMultiple5    = List.of(10, 32, 34, 36, 38, 41);
            List<Integer> withoutMultiple5 = List.of(11, 32, 34, 36, 38, 41);

            assertThat(scorer.score(withMultiple5)).isLessThan(scorer.score(withoutMultiple5));
        }
    }

    @Nested
    @DisplayName("7 배수 페널티")
    class MultipleOf7Penalty {

        @Test
        @DisplayName("7 배수 번호 하나를 포함하면 비배수로 바꾼 조합보다 점수가 낮다")
        void multipleOf7_lowersScore() {
            List<Integer> withMultiple7    = List.of(14, 32, 34, 36, 38, 41);
            List<Integer> withoutMultiple7 = List.of(13, 32, 34, 36, 38, 41);

            assertThat(scorer.score(withMultiple7)).isLessThan(scorer.score(withoutMultiple7));
        }
    }

    @Nested
    @DisplayName("연속 번호 페널티")
    class ConsecutivePairPenalty {

        @Test
        @DisplayName("연속 쌍이 있는 조합은 없는 조합보다 점수가 낮다")
        void consecutivePairs_lowersScore() {
            List<Integer> consecutive    = List.of(33, 34, 35, 38, 41, 44); // 33-34, 34-35 연속
            List<Integer> nonConsecutive = List.of(33, 36, 39, 41, 43, 45); // 연속 없음

            assertThat(scorer.score(consecutive)).isLessThan(scorer.score(nonConsecutive));
        }

        @Test
        @DisplayName("연속 쌍이 많을수록 점수가 더 낮다")
        void moreConsecutivePairs_lowerScore() {
            // 5·7 배수 없음, 모두 32 이상, 합계 > 220(구간 보너스 없음)으로 통일
            // → 연속 쌍 차이만 점수에 반영됨
            List<Integer> zeroPairs = List.of(32, 34, 37, 39, 41, 43); // 연속 쌍 0개, 점수 18
            List<Integer> onePair   = List.of(32, 33, 37, 39, 41, 43); // 연속 쌍 1개, 점수 17
            List<Integer> twoPairs  = List.of(32, 33, 34, 39, 41, 43); // 연속 쌍 2개, 점수 16

            assertThat(scorer.score(zeroPairs))
                    .isGreaterThan(scorer.score(onePair))
                    .isGreaterThan(scorer.score(twoPairs));
        }
    }

    @Nested
    @DisplayName("합계 구간 보너스/페널티")
    class SumRangeBonus {

        @Test
        @DisplayName("합계 130~220 구간은 보너스를 받는다")
        void sumInHighRange_getsBonus() {
            // 합계: 30+32+34+36+38+40 = 210
            List<Integer> highSumCombo = List.of(30, 32, 34, 36, 38, 40);
            // 동일 번호 구성이지만 합계를 낮추기 위해 낮은 번호로
            List<Integer> sameStructureLowSum = List.of(1, 2, 3, 4, 5, 6); // 합계 21

            assertThat(scorer.score(highSumCombo)).isGreaterThan(scorer.score(sameStructureLowSum));
        }

        @Test
        @DisplayName("합계 100 미만은 페널티를 받는다")
        void sumBelowHundred_getPenalty() {
            List<Integer> veryLowSum = List.of(1, 2, 3, 4, 5, 9);   // 합계 24
            List<Integer> midRange   = List.of(20, 25, 27, 30, 32, 35); // 합계 169

            assertThat(scorer.score(veryLowSum)).isLessThan(scorer.score(midRange));
        }
    }

    // ── 극단값 검증 ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("역대 최악 조합(1,2,3,4,5,6)은 음수 점수를 받는다")
    void score_worstCombo_isNegative() {
        assertThat(scorer.score(List.of(1, 2, 3, 4, 5, 6))).isNegative();
    }

    @Test
    @DisplayName("고번호와 고합계 조합은 양수 점수를 받는다")
    void score_bestStructuredCombo_isPositive() {
        assertThat(scorer.score(List.of(32, 36, 38, 40, 43, 45))).isPositive();
    }

    @Test
    @DisplayName("5 배수만으로 이루어진 조합(5,10,15,20,25,30)은 음수 점수를 받는다")
    void score_allMultiplesOf5_isNegative() {
        assertThat(scorer.score(List.of(5, 10, 15, 20, 25, 30))).isNegative();
    }

    @Test
    @DisplayName("휴리스틱 버전은 heuristic-v1로 명명·버전화되어 있다")
    void version_isHeuristicV1() {
        assertThat(CombinationScorer.VERSION).isEqualTo("heuristic-v1");
    }

    // ── 정확한 경계값·가중치 검증 (B-07: 뮤테이션 테스트로 드러난 공백 보완) ──
    //
    // 위 테스트들은 전부 "A가 B보다 높다/낮다"는 상대 비교라, 개별 가중치의 부호나
    // 크기가 조금 바뀌어도(예: -5 → +5, > 31 → >= 31) 다른 항이 차이를 압도해 통과해
    // 버릴 수 있다. 실제로 뮤테이션 테스트(./gradlew pitest)를 돌려보니 score() 안의
    // 개별 항 22곳 중 11곳이 이 방식으로는 안 잡혔다. 각 분기를 한 번에 하나씩만
    // 격리해 정확한 값으로 고정한다. 기준(baseline={11,13,16,18,22,24})은 어떤 분기도
    // 건드리지 않는 6개 조합으로, score()가 정확히 0을 반환한다.
    @Nested
    @DisplayName("개별 분기 정확값 고정(뮤테이션 가드)")
    class ExactBoundaryValues {

        @Test
        @DisplayName("32 이상 가산(+3)은 31에서는 발동하지 않고 32에서 정확히 발동한다")
        void over31Bonus_exactBoundaryAndMagnitude() {
            assertThat(scorer.score(List.of(11, 13, 16, 18, 22, 31))).isEqualTo(0);
            assertThat(scorer.score(List.of(11, 13, 16, 18, 22, 32))).isEqualTo(3);
        }

        @Test
        @DisplayName("한 자리 숫자 페널티는 정확히 -4다")
        void singleDigitPenalty_exactMagnitude() {
            assertThat(scorer.score(List.of(8, 13, 16, 18, 22, 24))).isEqualTo(-4);
        }

        @Test
        @DisplayName("5 배수 페널티는 정확히 -2다")
        void multipleOf5Penalty_exactMagnitude() {
            assertThat(scorer.score(List.of(10, 13, 16, 18, 22, 24))).isEqualTo(-2);
        }

        @Test
        @DisplayName("7 배수 페널티는 정확히 -1이다")
        void multipleOf7Penalty_exactMagnitude() {
            assertThat(scorer.score(List.of(13, 16, 18, 22, 24, 28))).isEqualTo(-1);
        }

        @Test
        @DisplayName("합계 130 미만(129)은 보너스가 없고, 130에서 정확히 +4 보너스가 발동한다")
        void sumBonusRange_exactBoundaryAndMagnitude() {
            assertThat(scorer.score(List.of(13, 16, 18, 24, 27, 31))).isEqualTo(0);
            assertThat(scorer.score(List.of(13, 16, 22, 24, 26, 29))).isEqualTo(4);
        }

        @Test
        @DisplayName("합계 100 미만 페널티(-5)는 99에서 발동하고 100에서는 발동하지 않는다")
        void sumPenaltyRange_exactBoundaryAndMagnitude() {
            // 두 조합 모두 18·19가 연속(-1 공통)이라 그 부분은 상쇄되고, sum<100 분기
            // 자체의 경계와 크기(-5)만 델타로 드러난다.
            assertThat(scorer.score(List.of(11, 13, 16, 18, 19, 22))).isEqualTo(-6); // 합계 99: -5(합계) -1(연속)
            assertThat(scorer.score(List.of(11, 13, 16, 18, 19, 23))).isEqualTo(-1); // 합계 100: -1(연속만)
        }

        @Test
        @DisplayName("아무 분기도 건드리지 않는 중립 조합은 정확히 0점이다")
        void neutralCombo_isExactlyZero() {
            assertThat(scorer.score(List.of(11, 13, 16, 18, 22, 24))).isEqualTo(0);
        }

        @Test
        @DisplayName("합계 220은 보너스 구간에 포함되고 221은 포함되지 않는다(상한 경계)")
        void sumBonusUpperBoundary_isInclusiveAt220() {
            // 두 조합 모두 32 이상 번호 5개(+15 공통)로 맞춰, 합계 상한 경계(<=220)의
            // 포함 여부와 크기(+4)만 델타로 드러나게 한다.
            assertThat(scorer.score(List.of(26, 33, 37, 39, 41, 44))).isEqualTo(19); // 합계 220: +15 +4
            assertThat(scorer.score(List.of(26, 34, 37, 39, 41, 44))).isEqualTo(15); // 합계 221: +15만
        }
    }
}
