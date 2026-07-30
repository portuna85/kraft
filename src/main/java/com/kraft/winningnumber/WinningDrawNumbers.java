package com.kraft.winningnumber;

import com.kraft.common.lotto.LottoBitmask;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * 한 회차의 당첨 번호 6개와 보너스 번호를 표현하는 불변 값 객체다.
 */
public record WinningDrawNumbers(List<Integer> mainNumbers, int bonusNumber) {

    private static final int MAIN_NUMBER_COUNT = 6;
    private static final int MIN_NUMBER = 1;
    private static final int MAX_NUMBER = 45;

    public WinningDrawNumbers {
        Objects.requireNonNull(mainNumbers, "mainNumbers");
        if (mainNumbers.size() != MAIN_NUMBER_COUNT) {
            throw new IllegalArgumentException("당첨 번호는 정확히 6개여야 합니다.");
        }

        List<Integer> sorted = new ArrayList<>(mainNumbers);
        if (sorted.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("당첨 번호에 null이 포함될 수 없습니다.");
        }
        if (sorted.stream().anyMatch(number -> !isInRange(number))) {
            throw new IllegalArgumentException("당첨 번호는 1부터 45 사이여야 합니다.");
        }
        if (new HashSet<>(sorted).size() != MAIN_NUMBER_COUNT) {
            throw new IllegalArgumentException("당첨 번호는 중복될 수 없습니다.");
        }
        if (!isInRange(bonusNumber)) {
            throw new IllegalArgumentException("보너스 번호는 1부터 45 사이여야 합니다.");
        }
        if (sorted.contains(bonusNumber)) {
            throw new IllegalArgumentException("보너스 번호는 당첨 번호와 중복될 수 없습니다.");
        }

        sorted.sort(Integer::compareTo);
        mainNumbers = List.copyOf(sorted);
    }

    public long combinationMask() {
        return LottoBitmask.of(mainNumbers);
    }

    private static boolean isInRange(int number) {
        return number >= MIN_NUMBER && number <= MAX_NUMBER;
    }
}
