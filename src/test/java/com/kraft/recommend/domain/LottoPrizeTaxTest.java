package com.kraft.recommend.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link LottoPrizeTax}의 구간별 원천징수 계산을 확인한다. */
class LottoPrizeTaxTest {

    @Test
    @DisplayName("5만원 이하는 비과세로 그대로 돌려준다")
    void taxFree_atOrBelowThreshold() {
        assertThat(LottoPrizeTax.afterTax(50_000L)).isEqualTo(50_000L);
        assertThat(LottoPrizeTax.afterTax(1_000L)).isEqualTo(1_000L);
    }

    @Test
    @DisplayName("5만원 초과 3억원 이하는 22%를 뗀다")
    void lowerBracket_twentyTwoPercent() {
        // 100,000,000 * 22% = 22,000,000
        assertThat(LottoPrizeTax.afterTax(100_000_000L)).isEqualTo(78_000_000L);
    }

    @Test
    @DisplayName("정확히 3억원 경계는 22% 구간에 포함된다")
    void bracketBoundary_isInLowerBracket() {
        // 300,000,000 * 22% = 66,000,000
        assertThat(LottoPrizeTax.afterTax(300_000_000L)).isEqualTo(234_000_000L);
    }

    @Test
    @DisplayName("3억원 초과분은 33%를 뗀다")
    void upperBracket_blendedRate() {
        // 3억 * 22% = 66,000,000 + 1억 * 33% = 33,000,000 -> 세금 99,000,000
        assertThat(LottoPrizeTax.afterTax(400_000_000L)).isEqualTo(301_000_000L);
    }
}
