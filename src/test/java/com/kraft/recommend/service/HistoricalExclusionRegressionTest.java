package com.kraft.recommend.service;

import com.kraft.recommend.domain.LottoBitmask;
import com.kraft.recommend.domain.LottoNumbers;
import com.kraft.recommend.domain.RecommendationHistorySnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 과거 1등 조합을 반환하지 않는다는 확정 정책의 회귀 검증. 이력이 빽빽한 상태에서 여러 시드로 뽑아도
 * 과거 1등 조합이 결과에 섞이지 않아야 한다.
 */
class HistoricalExclusionRegressionTest {

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

    /** 1..poolMax 안의 6개 조합을 사전순으로 나열한다. */
    private static List<List<Integer>> allCombinations(int poolMax) {
        List<List<Integer>> all = new ArrayList<>();
        for (int a = 1; a <= poolMax; a++)
            for (int b = a + 1; b <= poolMax; b++)
                for (int c = b + 1; c <= poolMax; c++)
                    for (int d = c + 1; d <= poolMax; d++)
                        for (int e = d + 1; e <= poolMax; e++)
                            for (int f = e + 1; f <= poolMax; f++)
                                all.add(List.of(a, b, c, d, e, f));
        return all;
    }

    private static RecommendationHistorySnapshot historyOf(List<List<Integer>> winners) {
        Set<Long> masks = winners.stream().map(LottoBitmask::maskOf).collect(Collectors.toSet());
        return new RecommendationHistorySnapshot(masks, winners.size(), winners.size(), winners.size(), 1L, Instant.now());
    }

    @Test
    @DisplayName("1~20 범위의 조합 전부를 1등 이력으로 넣어도, 어떤 시드로 뽑아도 그 조합은 나오지 않는다")
    void neverReturnsHistorical_evenWithDenseHistory() {
        // 1~20 안의 6개 조합은 38,760개다. 전체 공간(8,145,060개) 대비 작지만, 이 조합들이 결과에 섞이면 바로 잡힌다.
        List<List<Integer>> winners = allCombinations(20);
        RecommendationHistorySnapshot history = historyOf(winners);

        for (long seed = 0; seed < 200; seed++) {
            var generator = new RecommendationCandidateGenerator(new FixedRandomSource(seed));
            List<LottoNumbers> results = generator.generate(10, history);

            assertThat(results).hasSize(10);
            Set<Long> seen = new HashSet<>();
            for (LottoNumbers combo : results) {
                assertThat(history.winningMasks()).as("과거 1등 조합 %s가 반환됨", combo.numbers())
                        .doesNotContain(combo.mask());
                assertThat(seen.add(combo.mask())).as("요청 내 중복").isTrue();
            }
        }
    }
}
