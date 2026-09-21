package com.kraft.recommend.service;

import com.kraft.recommend.domain.LottoBitmask;
import com.kraft.recommend.domain.LottoNumbers;
import com.kraft.recommend.domain.RecommendationGenerationLimitException;
import com.kraft.recommend.domain.RecommendationHistorySnapshot;
import com.kraft.recommend.domain.RecommendationStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecommendationCandidateGeneratorTest {

    private static final RecommendationHistorySnapshot EMPTY_HISTORY =
            new RecommendationHistorySnapshot(Set.of(), 1, 1, 1, 1L, Instant.now());

    /** 테스트에서 고정 시드로 충돌·동점·상한 상황을 재현하기 위한 난수 소스. */
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

    private final RecommendationCandidateGenerator generator =
            new RecommendationCandidateGenerator(new FixedRandomSource(42L));

    @Test
    @DisplayName("random 전략은 요청 개수만큼 서로 다른 조합을 반환하고, 고정 포함·제외 미포함을 지킨다")
    void random_returnsDistinctValidCombinations() {
        NormalizedRecommendationRequest request = new NormalizedRecommendationRequest(
                5, RecommendationStrategy.RANDOM, Set.of(1, 2), Set.of(3, 4, 5));

        var results = generator.generateRandom(request, EMPTY_HISTORY);

        assertThat(results).hasSize(5);
        Set<Long> masks = new HashSet<>();
        for (LottoNumbers combo : results) {
            assertThat(combo.numbers()).hasSize(6);
            assertThat(combo.numbers()).containsAll(request.lockedNumbers());
            assertThat(combo.numbers()).noneMatch(request.excludedNumbers()::contains);
            assertThat(masks.add(combo.mask())).as("요청 내 중복 금지(NUM-07)").isTrue();
        }
    }

    @Test
    @DisplayName("과거 당첨 조합과 완전히 일치하는 유일한 후보뿐이면 상한 소진으로 실패한다")
    void random_whenOnlyPossibleComboIsHistorical_throwsGenerationLimit() {
        // 제외 39개(7~45)로 자유 번호를 1~6 여섯 개만 남기면 조합은 하나뿐이다.
        Set<Integer> excluded = IntStream.rangeClosed(7, 45).boxed()
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        long onlyPossibleMask = LottoBitmask.maskOf(java.util.List.of(1, 2, 3, 4, 5, 6));
        RecommendationHistorySnapshot historyWithOnlyCombo =
                new RecommendationHistorySnapshot(Set.of(onlyPossibleMask), 1, 1, 1, 1L, Instant.now());

        NormalizedRecommendationRequest request = new NormalizedRecommendationRequest(
                1, RecommendationStrategy.RANDOM, Set.of(), excluded);

        assertThatThrownBy(() -> generator.generateRandom(request, historyWithOnlyCombo))
                .isInstanceOf(RecommendationGenerationLimitException.class);
    }

    @Test
    @DisplayName("balanced 전략은 점수 내림차순으로 정렬되어 요청 개수만큼 반환된다")
    void balanced_returnsScoreDescendingResults() {
        NormalizedRecommendationRequest request = new NormalizedRecommendationRequest(
                5, RecommendationStrategy.BALANCED, Set.of(), Set.of());

        var results = generator.generateBalanced(request, EMPTY_HISTORY);

        assertThat(results).hasSize(5);
        for (int i = 1; i < results.size(); i++) {
            assertThat(results.get(i - 1).score()).isGreaterThanOrEqualTo(results.get(i).score());
        }
    }

    @Test
    @DisplayName("reduce_shared_winner_risk 전략은 요청 개수만큼 서로 다른 조합을 반환한다")
    void reduceSharedWinnerRisk_returnsDistinctCombinations() {
        NormalizedRecommendationRequest request = new NormalizedRecommendationRequest(
                3, RecommendationStrategy.REDUCE_SHARED_WINNER_RISK, Set.of(), Set.of());

        var results = generator.generateReduceSharedWinnerRisk(request, EMPTY_HISTORY);

        assertThat(results).hasSize(3);
        Set<Long> masks = new HashSet<>();
        results.forEach(combo -> assertThat(masks.add(combo.mask())).isTrue());
    }

    /**
     * B03: pickBestOfFifty가 usedMasks를 모른 채 "50개 중 최고"만 고르면, 좁은 조합 공간에서
     * 이미 반환한 조합을 계속 최고로 재선택해 collectionCap을 소진하고 예외를 던질 수 있다 —
     * verifyCombinationFeasibility가 이미 고유 조합이 충분함을 확인했음에도 그렇다. 고정
     * {1,2,3,4,5}, 자유 번호는 {6,8}뿐인 채로 count=2를 요청하면 가능한 조합은 정확히 2개
     * ({1..5,6}과 {1..5,8})뿐이다.
     */
    @Test
    @DisplayName("B03: 자유 번호가 요청 개수만큼만 남은 좁은 공간에서도 예외 없이 고유 조합을 채운다")
    void reduceSharedWinnerRisk_whenCombinationSpaceIsAsNarrowAsTheRequestCount_stillFillsWithoutThrowing() {
        Set<Integer> locked = Set.of(1, 2, 3, 4, 5);
        Set<Integer> excluded = IntStream.rangeClosed(7, 45)
                .filter(n -> n != 8)
                .boxed()
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));

        NormalizedRecommendationRequest request = new NormalizedRecommendationRequest(
                2, RecommendationStrategy.REDUCE_SHARED_WINNER_RISK, locked, excluded);

        var results = generator.generateReduceSharedWinnerRisk(request, EMPTY_HISTORY);

        assertThat(results).hasSize(2);
        Set<Long> masks = new HashSet<>();
        results.forEach(combo -> assertThat(masks.add(combo.mask())).isTrue());
    }
}
