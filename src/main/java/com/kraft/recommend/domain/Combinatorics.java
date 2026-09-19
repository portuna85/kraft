package com.kraft.recommend.domain;

/**
 * 조합 수 계산(01문서 3절: {@code C(45-E-L, 6-L)}). {@code n}은 45 이하이므로 {@code long}
 * 범위를 넘지 않는다.
 */
public final class Combinatorics {

    private Combinatorics() {
    }

    public static long nCr(int n, int r) {
        if (r < 0 || n < 0 || r > n) {
            return 0L;
        }
        long result = 1L;
        for (int i = 0; i < r; i++) {
            result = result * (n - i) / (i + 1);
        }
        return result;
    }
}
