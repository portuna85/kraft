package com.kraft.recommend.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link WinningDraw#applyDetails}의 부가 정보 정합성 검사(B14). 자동 수집 경로
 * ({@code DhLotteryClient})는 이미 범위 밖 보너스 번호를 null로 거르지만, 운영자가 직접
 * 넣는 수동/CSV 반영 경로는 그 방어를 거치지 않는다 — 이 검사가 그 경로의 최후 방어선이다.
 */
class WinningDrawTest {

    private static WinningDraw draw() {
        return WinningDraw.builder()
                .roundNo(1)
                .numbers(List.of(1, 2, 3, 4, 5, 6))
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("보너스 번호가 본번호와 중복되면 거절한다")
    void applyDetails_bonusDuplicatesMainNumber_throws() {
        assertThatThrownBy(() -> draw().applyDetails(new DrawDetails(3, LocalDate.now(), 1, 1_000L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("중복");
    }

    @Test
    @DisplayName("보너스 번호가 범위를 벗어나면 거절한다")
    void applyDetails_bonusOutOfRange_throws() {
        assertThatThrownBy(() -> draw().applyDetails(new DrawDetails(46, LocalDate.now(), 1, 1_000L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("범위");
    }

    @Test
    @DisplayName("1등 당첨자 수가 음수면 거절한다")
    void applyDetails_negativeWinnerCount_throws() {
        assertThatThrownBy(() -> draw().applyDetails(new DrawDetails(7, LocalDate.now(), -1, 1_000L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("음수");
    }

    @Test
    @DisplayName("1등 당첨금이 음수면 거절한다")
    void applyDetails_negativeAmount_throws() {
        assertThatThrownBy(() -> draw().applyDetails(new DrawDetails(7, LocalDate.now(), 1, -1_000L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("음수");
    }

    @Test
    @DisplayName("유효한 값과 null이 섞여 있어도 정상 반영된다")
    void applyDetails_validValuesWithNulls_applies() {
        WinningDraw winningDraw = draw();

        winningDraw.applyDetails(new DrawDetails(7, null, 1, null));

        assertThat(winningDraw.getBonusNo()).isEqualTo(7);
        assertThat(winningDraw.getDrawDate()).isNull();
        assertThat(winningDraw.getFirstPrizeWinnerCount()).isEqualTo(1);
        assertThat(winningDraw.getFirstPrizeAmount()).isNull();
    }
}
