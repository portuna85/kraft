package com.kraft.recommend.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link WinningDrawRepository#findTopByOrderByRoundNoDesc()}가 최신 회차를 찾는지 확인한다. */
@DataJpaTest
class WinningDrawRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private WinningDrawRepository repository;

    @Test
    @DisplayName("회차가 없으면 빈 값을 반환한다")
    void empty_whenNoDraws() {
        Optional<WinningDraw> result = repository.findTopByOrderByRoundNoDesc();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("가장 큰 회차를 반환한다")
    void returnsHighestRound() {
        seed(1, List.of(1, 2, 3, 4, 5, 6));
        seed(3, List.of(7, 13, 16, 23, 24, 43));
        seed(2, List.of(11, 13, 22, 32, 33, 36));

        Optional<WinningDraw> result = repository.findTopByOrderByRoundNoDesc();

        assertThat(result).isPresent();
        assertThat(result.get().getRoundNo()).isEqualTo(3);
        assertThat(result.get().numbers()).containsExactly(7, 13, 16, 23, 24, 43);
    }

    private void seed(int round, List<Integer> numbers) {
        em.persist(WinningDraw.builder()
                .roundNo(round)
                .numbers(numbers)
                .updatedAt(LocalDateTime.now())
                .build());
        em.flush();
    }
}
