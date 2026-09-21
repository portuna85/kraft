package com.kraft.recommend.domain;

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
        return new LottoNumbers(List.copyOf(unique), LottoBitmask.maskOf(unique));
    }

    /**
     * 항상 불변 목록을 돌려준다(B14/P3) — 호출부가 이 참조를 들고 있다가 나중에 바꾸면
     * 번호와 {@link #mask}가 어긋날 수 있다. 실제로 그런 변조 경로가 확인된 것은 아니고,
     * 경계에서 방어적으로 막아 둔다.
     */
    public List<Integer> numbers() {
        return numbers;
    }

    public long mask() {
        return mask;
    }
}
