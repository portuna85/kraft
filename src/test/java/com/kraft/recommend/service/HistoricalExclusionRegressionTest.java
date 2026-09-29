package com.kraft.recommend.service;

import com.kraft.recommend.domain.LottoBitmask;
import com.kraft.recommend.domain.LottoNumbers;
import com.kraft.recommend.domain.RecommendationHistorySnapshot;
import com.kraft.recommend.domain.RecommendationStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 세 전략 모두 과거 1등 조합을 반환하지 않는다는 확정 정책(improvement.md 0장, P1-4)의 회귀 검증.
 * 전수 열거 경로(허용 조합 ≤ 2000)와 표본 추출 경로를 각각 이력이 빽빽한 상태로 확인한다.
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

    private static Set<Integer> excludedAbove(int poolMax) {
        return IntStream.rangeClosed(poolMax + 1, 45).boxed().collect(Collectors.toSet());
    }

    private static RecommendationHistorySnapshot historyOf(List<List<Integer>> winners) {
        Set<Long> masks = winners.stream().map(LottoBitmask::maskOf).collect(Collectors.toSet());
        return new RecommendationHistorySnapshot(masks, winners.size(), winners.size(), winners.size(), 1L, Instant.now());
    }

    private static void assertNoneHistorical(List<Set<Integer>> results, RecommendationHistorySnapshot history, int expectedCount) {
        assertThat(results).hasSize(expectedCount);
        Set<Long> seen = new HashSet<>();
        for (Set<Integer> numbers : results) {
            long mask = LottoBitmask.maskOf(numbers);
            assertThat(history.winningMasks()).as("과거 1등 조합 %s가 반환됨", numbers).doesNotContain(mask);
            assertThat(seen.add(mask)).as("요청 내 중복").isTrue();
        }
    }

    private List<Set<Integer>> generate(RecommendationStrategy strategy, RecommendationCandidateGenerator generator,
                                        NormalizedRecommendationRequest request,
                                        RecommendationHistorySnapshot history, long allowedPossible) {
        return switch (strategy) {
            case RANDOM -> generator.generateRandom(request, history, allowedPossible).stream()
                    .map(LottoNumbers::numbers).<Set<Integer>>map(Set::copyOf).toList();
            case BALANCED -> generator.generateBalanced(request, history, allowedPossible).stream()
                    .map(c -> c.numbers().numbers()).<Set<Integer>>map(Set::copyOf).toList();
            case REDUCE_SHARED_WINNER_RISK -> generator.generateReduceSharedWinnerRisk(request, history, allowedPossible)
                    .stream().map(LottoNumbers::numbers).<Set<Integer>>map(Set::copyOf).toList();
        };
    }

    @Test
    @DisplayName("전수 열거 경로: 허용 조합의 절반이 과거 1등이어도 세 전략 모두 그 조합을 반환하지 않는다")
    void enumerationPath_neverReturnsHistorical() {
        List<List<Integer>> all = allCombinations(12);
        List<List<Integer>> winners = all.subList(0, 500);
        RecommendationHistorySnapshot history = historyOf(winners);
        long allowed = all.size() - winners.size();

        for (RecommendationStrategy strategy : RecommendationStrategy.values()) {
            for (long seed = 0; seed < 20; seed++) {
                var generator = new RecommendationCandidateGenerator(new FixedRandomSource(seed));
                var request = new NormalizedRecommendationRequest(5, strategy, Set.of(), excludedAbove(12));
                assertNoneHistorical(generate(strategy, generator, request, history, allowed), history, 5);
            }
        }
    }

    @Test
    @DisplayName("표본 추출 경로: 후보 공간의 대부분이 과거 1등이어도 세 전략 모두 그 조합을 반환하지 않는다")
    void samplingPath_neverReturnsHistorical() {
        List<List<Integer>> all = allCombinations(20);
        List<List<Integer>> winners = all.subList(0, 30_000);
        RecommendationHistorySnapshot history = historyOf(winners);
        long allowed = all.size() - winners.size();
        assertThat(allowed).isGreaterThan(2000);

        for (RecommendationStrategy strategy : RecommendationStrategy.values()) {
            for (long seed = 0; seed < 5; seed++) {
                var generator = new RecommendationCandidateGenerator(new FixedRandomSource(seed));
                var request = new NormalizedRecommendationRequest(5, strategy, Set.of(), excludedAbove(20));
                assertNoneHistorical(generate(strategy, generator, request, history, allowed), history, 5);
            }
        }
    }
}
