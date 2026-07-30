package com.kraft.common.lotto;

import java.util.Collection;

/** 로또 번호 6개(1~45)를 45비트 이내의 long으로 인코딩한다(ball n → bit n-1, 정렬 여부 무관). */
public final class LottoBitmask {

    private LottoBitmask() {
    }

    public static long of(int n1, int n2, int n3, int n4, int n5, int n6) {
        return (1L << (n1 - 1)) | (1L << (n2 - 1)) | (1L << (n3 - 1))
                | (1L << (n4 - 1)) | (1L << (n5 - 1)) | (1L << (n6 - 1));
    }

    public static long of(Collection<Integer> numbers) {
        long mask = 0L;
        for (int number : numbers) {
            mask |= 1L << (number - 1);
        }
        return mask;
    }
}
