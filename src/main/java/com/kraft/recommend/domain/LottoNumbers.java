package com.kraft.recommend.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 검증된 로또 조합 하나(서로 다른 1~45 정수 6개, 오름차순). 이 타입의 인스턴스는 항상
 * NUM-01/NUM-02를 만족한다 — 생성자에서 강제한다.
 */
public final class LottoNumbers {

    public static final int SIZE = 6;
    public static final int MIN = 1;
    public static final int MAX = 45;

    private final List<Integer> numbers;
    private final long mask;

    private LottoNumbers(List<Integer> sorted, long mask) {
        this.numbers = sorted;
        this.mask = mask;
    }

    public static LottoNumbers of(Collection<Integer> input) {
        Set<Integer> unique = new TreeSet<>(input);
        if (unique.size() != SIZE || input.size() != SIZE) {
            throw new IllegalArgumentException("로또 조합은 서로 다른 번호 6개여야 합니다.");
        }
        for (int number : unique) {
            if (number < MIN || number > MAX) {
                throw new IllegalArgumentException("번호는 1~45 범위여야 합니다: " + number);
            }
        }
        return new LottoNumbers(new ArrayList<>(unique), LottoBitmask.maskOf(unique));
    }

    public List<Integer> numbers() {
        return numbers;
    }

    public long mask() {
        return mask;
    }
}
