package com.kraft.recommend;

import com.kraft.common.error.ApiException;
import com.kraft.common.lotto.LottoNumberCodec;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("추천 후보 생성기 단위 테스트(TD-008 3단계)")
class RecommendationCandidateGeneratorTest {

    private RecommendationCandidateGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new RecommendationCandidateGenerator(
                new LottoNumberCodec(), new CombinationScorer(), new BalancedScorer(), new SimpleMeterRegistry());
    }

    private static RecommendationHistorySnapshotManager.HistorySnapshot snapshotWithMasks(Set<Long> masks) {
        return new RecommendationHistorySnapshotManager.HistorySnapshot(masks, 1, 1, null, 1L, Instant.EPOCH);
    }

    private static RecommendationRequestValidator.RequestContext ctx(int count, String strategy,
            List<Integer> locked, Set<Integer> excluded) {
        return new RecommendationRequestValidator.RequestContext(count, strategy, locked, excluded);
    }

    @Test
    @DisplayName("random 전략은 요청한 개수만큼 유일하고 정렬된 조합을 반환한다")
    void sampleRecommendations_random_returnsRequestedUniqueSortedCombos() {
        var result = generator.sampleRecommendations(
                ctx(5, "random", List.of(), Set.of()), snapshotWithMasks(Set.of()),
                bound -> java.util.concurrent.ThreadLocalRandom.current().nextInt(bound));

        assertThat(result).hasSize(5);
        Set<Long> seenMasks = new HashSet<>();
        for (RecommendationItemView item : result) {
            assertThat(item.numbers()).hasSize(6).doesNotHaveDuplicates().isSorted();
            assertThat(seenMasks.add(
                    item.numbers().stream().mapToLong(n -> 1L << (n - 1)).reduce(0L, (a, b) -> a | b)))
                    .as("조합 간 중복 없음").isTrue();
        }
    }

    @Test
    @DisplayName("balanced 전략은 요청한 개수만큼 반환하고 점수 내림차순으로 정렬한다")
    void sampleRecommendations_balanced_returnsRequestedCountSortedByScoreDesc() {
        var result = generator.sampleRecommendations(
                ctx(3, "balanced", List.of(), Set.of()), snapshotWithMasks(Set.of()),
                bound -> java.util.concurrent.ThreadLocalRandom.current().nextInt(bound));

        assertThat(result).hasSize(3);
        for (int i = 0; i < result.size() - 1; i++) {
            assertThat(result.get(i).score()).isGreaterThanOrEqualTo(result.get(i + 1).score());
        }
    }

    @Test
    @DisplayName("reduce_shared_winner_risk 전략은 요청한 개수만큼 유효한 조합을 반환한다")
    void sampleRecommendations_reduceSharedWinnerRisk_returnsValidCombos() {
        var result = generator.sampleRecommendations(
                ctx(2, "reduce_shared_winner_risk", List.of(), Set.of()), snapshotWithMasks(Set.of()),
                bound -> java.util.concurrent.ThreadLocalRandom.current().nextInt(bound));

        assertThat(result).hasSize(2);
        result.forEach(item -> assertThat(item.numbers()).hasSize(6).doesNotHaveDuplicates().isSorted());
    }

    @Test
    @DisplayName("고정 번호는 결과에 항상 포함되고 제외 번호는 결코 포함되지 않는다")
    void sampleRecommendations_respectsLockedAndExcluded() {
        List<Integer> locked = List.of(1, 2);
        Set<Integer> excluded = Set.of(40, 41, 42, 43, 44, 45);

        var result = generator.sampleRecommendations(
                ctx(3, "random", locked, excluded), snapshotWithMasks(Set.of()),
                bound -> java.util.concurrent.ThreadLocalRandom.current().nextInt(bound));

        result.forEach(item -> {
            assertThat(item.numbers()).containsAll(locked);
            assertThat(item.numbers()).doesNotContainAnyElementsOf(excluded);
        });
    }

    @Test
    @DisplayName("결정론적 난수 소스가 항상 과거 1등 조합만 만들면 부분 결과 대신 명시적으로 실패한다")
    void sampleRecommendations_random_deterministicSourceAlwaysHitsHistorical_throws() {
        // 38개 제외 → 후보 7개(1~7). randomSource가 항상 0을 반환하면(스왑 없음) 매 시도
        // candidates 앞 6개(1,2,3,4,5,6)만 반복 생성한다. 그 조합을 과거 1등으로 등록하면
        // 이 난수 소스로는 count=1도 채울 수 없다.
        List<Integer> excluded = IntStream.rangeClosed(8, 45).boxed().collect(Collectors.toList());
        Set<Integer> excludedSet = Set.copyOf(excluded);
        long historicalMask = com.kraft.common.lotto.LottoBitmask.of(List.of(1, 2, 3, 4, 5, 6));

        assertThatThrownBy(() -> generator.sampleRecommendations(
                ctx(1, "random", List.of(), excludedSet), snapshotWithMasks(Set.of(historicalMask)),
                bound -> 0))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode())
                        .isEqualTo("INSUFFICIENT_UNIQUE_COMBINATIONS"));
    }

    @Test
    @DisplayName("balanced 전략도 count를 못 채우면 부분 결과 대신 명시적으로 실패한다")
    void sampleRecommendations_balanced_deterministicSourceLimitsPool_throws() {
        // 38개 제외 → 후보 7개(1~7). randomSource를 0으로 고정하면 매번 같은 조합만
        // 생성돼 seen 중복 체크에 걸려 풀이 1개에서 늘지 않는다 — count=2는 채울 수 없다.
        List<Integer> excluded = IntStream.rangeClosed(8, 45).boxed().collect(Collectors.toList());
        Set<Integer> excludedSet = Set.copyOf(excluded);

        assertThatThrownBy(() -> generator.sampleRecommendations(
                ctx(2, "balanced", List.of(), excludedSet), snapshotWithMasks(Set.of()),
                bound -> 0))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode())
                        .isEqualTo("INSUFFICIENT_UNIQUE_COMBINATIONS"));
    }
}
