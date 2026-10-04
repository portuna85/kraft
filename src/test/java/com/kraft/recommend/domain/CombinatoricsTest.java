package com.kraft.recommend.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class CombinatoricsTest {

    @ParameterizedTest
    @CsvSource({
            "45, 6, 8145060",   // 로또 6/45 전체 조합 수
            "5, 2, 10",
            "10, 0, 1",
            "10, 10, 1",
            "1, 1, 1",
            "44, 6, 7059052",
    })
    @DisplayName("nCr은 알려진 조합 수와 같다")
    void nCr_knownValues(int n, int r, long expected) {
        assertThat(Combinatorics.nCr(n, r)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"3, 5", "-1, 2", "5, -1", "0, 1"})
    @DisplayName("r이 n보다 크거나 음수가 있으면 0이다")
    void nCr_invalidArguments_returnsZero(int n, int r) {
        assertThat(Combinatorics.nCr(n, r)).isZero();
    }

    @Test
    @DisplayName("대칭이다: C(n, r) == C(n, n-r)")
    void nCr_isSymmetric() {
        for (int r = 0; r <= 45; r++) {
            assertThat(Combinatorics.nCr(45, r)).isEqualTo(Combinatorics.nCr(45, 45 - r));
        }
    }

    @Test
    @DisplayName("파스칼 삼각형 관계 C(n, r) = C(n-1, r-1) + C(n-1, r)를 만족한다")
    void nCr_satisfiesPascalsRule() {
        for (int n = 1; n <= 45; n++) {
            for (int r = 1; r < n; r++) {
                assertThat(Combinatorics.nCr(n, r))
                        .isEqualTo(Combinatorics.nCr(n - 1, r - 1) + Combinatorics.nCr(n - 1, r));
            }
        }
    }
}
