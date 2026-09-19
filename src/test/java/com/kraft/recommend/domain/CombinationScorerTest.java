package com.kraft.recommend.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CombinationScorerTest {

    @Test
    @DisplayName("32~45 번호는 개당 +3점")
    void highNumbers_addThreeEach() {
        // 33,38(32~45, +3*2=+6), 나머지 11,13,17,19는 저수·5·7배수·연속쌍에 해당하지 않는 중립값
        int score = CombinationScorer.score(List.of(11, 13, 17, 19, 33, 38));
        // sum = 11+13+17+19+33+38 = 131 -> +4(130~220 보너스); 연속쌍 없음
        assertThat(score).isEqualTo(6 + 4);
    }

    @Test
    @DisplayName("1~9 번호는 개당 -4점")
    void lowNumbers_subtractFourEach() {
        // 1,3(1~9, -4*2=-8), 나머지 11,13,17,19는 고저·5·7배수·연속쌍에 해당하지 않는 중립값
        int score = CombinationScorer.score(List.of(1, 3, 11, 13, 17, 19));
        // sum = 1+3+11+13+17+19 = 64 -> <100 => -5; 연속쌍 없음
        assertThat(score).isEqualTo(-8 - 5);
    }

    @Test
    @DisplayName("35는 32 이상 가점과 5·7 배수 감점이 모두 중첩 적용된다")
    void number35_overlapsMultipleRules() {
        // isolate 35's contribution by comparing two combos differing only by 35 vs 34 (34: only +3, no 5/7 multiple)
        List<Integer> with35 = List.of(1, 10, 11, 12, 13, 35);
        List<Integer> with34 = List.of(1, 10, 11, 12, 13, 34);
        int diff = CombinationScorer.score(with35) - CombinationScorer.score(with34);
        // 35: +3 (32~45) -2 (mult of5) -1 (mult of7) = 0 relative to neutral baseline number
        // 34: +3 (32~45) only
        // so diff = 0 - 3 = -3, plus any sum-bracket shift; compute sums:
        // sum(with35)=1+10+11+12+13+35=82 -> <100 => -5 penalty
        // sum(with34)=1+10+11+12+13+34=81 -> <100 => -5 penalty (same bracket, cancels in diff)
        assertThat(diff).isEqualTo((3 - 2 - 1) - 3);
    }

    @Test
    @DisplayName("합계 100 미만이면 -5점, 130~220이면 +4점, 그 사이(100~129)는 가감점이 없다")
    void sumBrackets() {
        // sum=21<100: lows 1~9 전부(-4*6=-24) + 5배수(5, -2) + 연속쌍 5개(-5) + 합계<100(-5) = -36
        assertThat(CombinationScorer.score(List.of(1, 2, 3, 4, 5, 6))).isEqualTo(-36);

        // sum=115(100~129, 가감점 없음): 5·7배수 없음, 고저 가점 없음, 연속쌍 4개(-4) = -4
        assertThat(CombinationScorer.score(List.of(16, 17, 18, 19, 22, 23))).isEqualTo(-4);

        // sum=180(130~220): 32~45(33,34, +3*2=+6) + 연속쌍 2개(-2) + 합계 보너스(+4) = 8
        assertThat(CombinationScorer.score(List.of(26, 27, 29, 31, 33, 34))).isEqualTo(8);
    }
}
