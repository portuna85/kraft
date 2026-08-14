package com.kraft.recommend;

import com.kraft.common.error.ApiException;
import com.kraft.common.lotto.LottoNumberCodec;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("추천 요청 검증기 단위 테스트(TD-008 2단계)")
class RecommendationRequestValidatorTest {

    private RecommendationRequestValidator validator;

    @BeforeEach
    void setUp() {
        validator = new RecommendationRequestValidator(new LottoNumberCodec(), new SimpleMeterRegistry());
    }

    private static RecommendationHistorySnapshotManager.HistorySnapshot emptySnapshot() {
        return new RecommendationHistorySnapshotManager.HistorySnapshot(
                Set.of(), 1, 1, null, 1L, Instant.EPOCH);
    }

    // ── parseRequest ─────────────────────────────────────────────────────

    @Test
    @DisplayName("요청이 null이면 count=1, strategy=random, 고정·제외 없음으로 해석한다")
    void parseRequest_nullRequest_defaultsToRandomCountOne() {
        RecommendationRequestValidator.RequestContext ctx = validator.parseRequest(null);

        assertThat(ctx.count()).isEqualTo(1);
        assertThat(ctx.strategy()).isEqualTo("random");
        assertThat(ctx.locked()).isEmpty();
        assertThat(ctx.excluded()).isEmpty();
    }

    @Test
    @DisplayName("count가 명시되면 그 값을 그대로 쓴다")
    void parseRequest_explicitCount_isUsed() {
        RecommendationRequestValidator.RequestContext ctx =
                validator.parseRequest(new RecommendNumbersRequest(3, null, null, null, null, null));

        assertThat(ctx.count()).isEqualTo(3);
    }

    @Test
    @DisplayName("고정 번호가 6개를 초과하면 TOO_MANY_LOCKED_NUMBERS로 거절한다")
    void parseRequest_tooManyLockedNumbers_throws() {
        List<Integer> tooManyLocked = List.of(1, 2, 3, 4, 5, 6);

        assertThatThrownBy(() ->
                validator.parseRequest(new RecommendNumbersRequest(1, null, null, null, tooManyLocked, null)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("TOO_MANY_LOCKED_NUMBERS"));
    }

    @Test
    @DisplayName("고정 번호와 제외 번호가 겹치면 LOCKED_EXCLUDED_CONFLICT로 거절한다")
    void parseRequest_lockedExcludedOverlap_throws() {
        assertThatThrownBy(() ->
                validator.parseRequest(new RecommendNumbersRequest(
                        1, List.of(7), null, null, List.of(7), null)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("LOCKED_EXCLUDED_CONFLICT"));
    }

    @Test
    @DisplayName("strategy 파라미터가 명시되면 대소문자 무관하게 정규화해 신뢰한다")
    void parseRequest_explicitStrategy_normalizedAndTrusted() {
        RecommendationRequestValidator.RequestContext ctx =
                validator.parseRequest(new RecommendNumbersRequest(1, null, null, "BALANCED", null, null));

        assertThat(ctx.strategy()).isEqualTo("balanced");
    }

    @Test
    @DisplayName("알 수 없는 strategy 값은 INVALID_RECOMMENDATION_STRATEGY로 거절한다")
    void parseRequest_unknownStrategy_throws() {
        assertThatThrownBy(() ->
                validator.parseRequest(new RecommendNumbersRequest(1, null, null, "not_a_strategy", null, null)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex ->
                        assertThat(((ApiException) ex).getCode()).isEqualTo("INVALID_RECOMMENDATION_STRATEGY"));
    }

    @Test
    @DisplayName("strategy가 없고 reduceSharedWinnerRisk가 true면 reduce_shared_winner_risk로 해석한다")
    void parseRequest_reduceSharedWinnerRiskFlag_mapsToStrategy() {
        RecommendationRequestValidator.RequestContext ctx =
                validator.parseRequest(new RecommendNumbersRequest(1, null, true, null, null, null));

        assertThat(ctx.strategy()).isEqualTo("reduce_shared_winner_risk");
    }

    @Test
    @DisplayName("strategy와 reduceSharedWinnerRisk가 둘 다 없으면 random이다")
    void parseRequest_noStrategyNoFlag_defaultsToRandom() {
        RecommendationRequestValidator.RequestContext ctx =
                validator.parseRequest(new RecommendNumbersRequest(1, null, false, null, null, null));

        assertThat(ctx.strategy()).isEqualTo("random");
    }

    @Test
    @DisplayName("strategy가 있으면 구 필드명(maximizePrize/reduceSharedWinnerRisk)보다 우선한다")
    void parseRequest_explicitStrategyOverridesLegacyFlag() {
        RecommendationRequestValidator.RequestContext ctx =
                validator.parseRequest(new RecommendNumbersRequest(1, null, false, "balanced", null, null));

        assertThat(ctx.strategy()).isEqualTo("balanced");
    }

    // ── validateFeasibility ──────────────────────────────────────────────

    @Test
    @DisplayName("제외 번호가 40개면(6개 미만 남음) TOO_MANY_EXCLUSIONS로 거절한다")
    void validateFeasibility_tooManyExclusions_throws() {
        List<Integer> excluded = java.util.stream.IntStream.rangeClosed(1, 40).boxed().toList();
        RecommendationRequestValidator.RequestContext ctx = validator.parseRequest(
                new RecommendNumbersRequest(1, excluded, null, null, null, null));

        assertThatThrownBy(() -> validator.validateFeasibility(ctx, emptySnapshot()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("TOO_MANY_EXCLUSIONS"));
    }

    @Test
    @DisplayName("가능한 고유 조합 수보다 많은 count를 요청하면 INSUFFICIENT_UNIQUE_COMBINATIONS로 거절한다")
    void validateFeasibility_countExceedsAllowedPossible_throws() {
        // 39개 제외 → 후보 6개(1~6) → C(6,6)=1가지뿐. count=2는 그 이상을 요구한다.
        List<Integer> excluded = java.util.stream.IntStream.rangeClosed(7, 45).boxed().toList();
        RecommendationRequestValidator.RequestContext ctx = validator.parseRequest(
                new RecommendNumbersRequest(2, excluded, null, null, null, null));

        assertThatThrownBy(() -> validator.validateFeasibility(ctx, emptySnapshot()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex ->
                        assertThat(((ApiException) ex).getCode()).isEqualTo("INSUFFICIENT_UNIQUE_COMBINATIONS"));
    }

    @Test
    @DisplayName("역대 1등 조합과 겹치는 만큼 가능한 조합 수에서 제외한다")
    void validateFeasibility_excludesHistoricalCombinationsFromAllowedCount() {
        // 39개 제외 → 후보 6개(1~6) → C(6,6)=1가지뿐이며 그 조합이 과거 1등이면 allowedPossible=0.
        List<Integer> excluded = java.util.stream.IntStream.rangeClosed(7, 45).boxed().toList();
        RecommendationRequestValidator.RequestContext ctx = validator.parseRequest(
                new RecommendNumbersRequest(1, excluded, null, null, null, null));
        long historicalMask = com.kraft.common.lotto.LottoBitmask.of(List.of(1, 2, 3, 4, 5, 6));
        RecommendationHistorySnapshotManager.HistorySnapshot snapshot =
                new RecommendationHistorySnapshotManager.HistorySnapshot(
                        Set.of(historicalMask), 1, 1, null, 1L, Instant.EPOCH);

        assertThatThrownBy(() -> validator.validateFeasibility(ctx, snapshot))
                .isInstanceOf(ApiException.class)
                .satisfies(ex ->
                        assertThat(((ApiException) ex).getCode()).isEqualTo("INSUFFICIENT_UNIQUE_COMBINATIONS"));
    }

    @Test
    @DisplayName("가능한 조합 수 이내면 통과한다")
    void validateFeasibility_withinAllowedCount_passes() {
        RecommendationRequestValidator.RequestContext ctx = validator.parseRequest(
                new RecommendNumbersRequest(1, null, null, null, null, null));

        org.assertj.core.api.Assertions.assertThatCode(() -> validator.validateFeasibility(ctx, emptySnapshot()))
                .doesNotThrowAnyException();
    }
}
