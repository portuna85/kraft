package com.kraft.recommend.domain;

import java.util.Collection;

/**
 * 로또 번호 1~45를 {@code long} 비트마스크로 표현한다(번호 n → {@code 1L << (n-1)}). 45비트가
 * 필요하므로 반드시 {@code long}을 쓴다({@code int}는 32비트라 33번 이상을 표현할 수 없다).
 * <p>
 * 이 클래스는 번호 검증을 하지 않는다 — 호출자가 1~45 범위의 검증된 번호만 넘겨야 한다
 * (docs/01-number-recommendation-source-analysis.md 3절).
 */
public final class LottoBitmask {

    private LottoBitmask() {
    }

    public static long bit(int number) {
        return 1L << (number - 1);
    }

    public static long maskOf(Collection<Integer> numbers) {
        long mask = 0L;
        for (int number : numbers) {
            mask |= bit(number);
        }
        return mask;
    }

    public static boolean intersects(long a, long b) {
        return (a & b) != 0L;
    }

    public static boolean contains(long mask, int number) {
        return (mask & bit(number)) != 0L;
    }
}
