package com.kraft.winningnumber;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class WinningDrawNumbersTest {

    @Test
    void sortsAndCopiesMainNumbers() {
        List<Integer> source = new java.util.ArrayList<>(List.of(6, 1, 5, 2, 4, 3));

        WinningDrawNumbers numbers = new WinningDrawNumbers(source, 7);
        source.set(0, 45);

        assertThat(numbers.mainNumbers()).containsExactly(1, 2, 3, 4, 5, 6);
        assertThat(numbers.combinationMask()).isEqualTo(63L);
    }

    @Test
    void rejectsInvalidMainAndBonusNumbers() {
        assertThatThrownBy(() -> new WinningDrawNumbers(List.of(1, 2, 3, 4, 5), 6))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WinningDrawNumbers(List.of(1, 2, 3, 4, 5, 5), 6))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WinningDrawNumbers(List.of(0, 2, 3, 4, 5, 6), 7))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WinningDrawNumbers(List.of(1, 2, 3, 4, 5, 6), 6))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
