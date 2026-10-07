package com.kraft.recommend.service;

import com.kraft.recommend.domain.LottoBitmask;
import com.kraft.recommend.domain.LottoNumbers;
import com.kraft.recommend.domain.RecommendationGenerationLimitException;
import com.kraft.recommend.domain.RecommendationHistorySnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecommendationCandidateGeneratorTest {

    private static final RecommendationHistorySnapshot EMPTY_HISTORY =
            new RecommendationHistorySnapshot(Set.of(), 1, 1, 1, 1L, Instant.now());

    /** 테스트에서 고정 시드로 결과를 재현하기 위한 난수 소스. */
    private static final class FixedRandomSource implements RecommendationRandomSource {
        private final Random random;

        FixedRandomSource(long seed) {
            this.random = new Random(seed);
        }

        @Override
        public Random current() {
            return random;
        }
    }

    /** 항상 같은 값만 돌려줘 매번 같은 조합이 뽑히게 하는 난수. 반복 상한 소진을 재현한다. */
    private static final class ConstantRandom extends Random {
        @Override
        public int nextInt(int bound) {
            return 0;
        }
    }

    private final RecommendationCandidateGenerator generator =
            new RecommendationCandidateGenerator(new FixedRandomSource(42L));

    @Test
    @DisplayName("요청 개수만큼 서로 다른 6개짜리 조합을 반환한다")
    void generate_returnsDistinctCombinations() {
        var results = generator.generate(5, EMPTY_HISTORY);

        assertThat(results).hasSize(5);
        Set<Long> masks = new HashSet<>();
        for (LottoNumbers combo : results) {
            assertThat(combo.numbers()).hasSize(6);
            assertThat(masks.add(combo.mask())).as("요청 내 중복 금지").isTrue();
        }
    }

    @Test
    @DisplayName("같은 조합만 계속 뽑히면 반복 상한을 소진하고 생성 한도 예외를 던진다")
    void generate_whenSameCombinationKeepsComingUp_throwsGenerationLimit() {
        var constantSource = (RecommendationRandomSource) ConstantRandom::new;
        var constantGenerator = new RecommendationCandidateGenerator(constantSource);

        assertThatThrownBy(() -> constantGenerator.generate(2, EMPTY_HISTORY))
                .isInstanceOf(RecommendationGenerationLimitException.class);
    }

    @Test
    @DisplayName("역대 1등 조합이 곧 뽑히는 조합이어도 그 조합은 반환하지 않는다")
    void generate_neverReturnsHistoricalCombination() {
        // 같은 시드로 먼저 한 번 뽑아 그 조합을 이력으로 넣고, 같은 시드로 다시 뽑는다.
        LottoNumbers firstDraw = new RecommendationCandidateGenerator(new FixedRandomSource(7L))
                .generate(1, EMPTY_HISTORY).get(0);
        RecommendationHistorySnapshot history = new RecommendationHistorySnapshot(
                Set.of(LottoBitmask.maskOf(firstDraw.numbers())), 1, 1, 1, 1L, Instant.now());

        List<LottoNumbers> results = new RecommendationCandidateGenerator(new FixedRandomSource(7L))
                .generate(10, history);

        assertThat(results).hasSize(10);
        assertThat(results).extracting(LottoNumbers::mask).doesNotContain(firstDraw.mask());
    }
}
