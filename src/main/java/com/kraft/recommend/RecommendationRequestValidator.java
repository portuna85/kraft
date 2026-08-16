package com.kraft.recommend;

import com.kraft.common.error.ApiErrorCode;
import com.kraft.common.error.ApiException;
import com.kraft.common.lotto.LottoBitmask;
import com.kraft.common.lotto.LottoNumberCodec;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * TD-008 2단계: {@link LottoRecommendationService}에서 요청 파싱과 실현가능성(조합 수)
 * 검증을 분리했다. 무상태다. REF-01: {@link RecommendationHistorySnapshotManager}와 같은
 * 이유로 Spring 빈이 아니었으나, 생성자 주입으로 전환해도 테스트에서 여전히 {@code new}로
 * 직접 만들 수 있어 실익이 없던 제약이었다.
 *
 * strategy 문자열 상수({@code STRATEGY_RANDOM} 등)는 후보 생성 디스패치에서도 쓰여
 * {@link LottoRecommendationService}에 그대로 둔다 — 패키지 프라이빗이라 여기서도
 * 그대로 참조한다.
 */
@Component
final class RecommendationRequestValidator {

    private static final int MAX_LOCKED_NUMBERS = 5;

    private final LottoNumberCodec lottoNumberCodec;
    private final MeterRegistry meterRegistry;

    RecommendationRequestValidator(LottoNumberCodec lottoNumberCodec, MeterRegistry meterRegistry) {
        this.lottoNumberCodec = lottoNumberCodec;
        this.meterRegistry = meterRegistry;
    }

    // B-08: 검증·메트릭·조합·샘플링이 한 메서드에 섞여 있던 것을, 로직은 그대로 두고
    // "요청 해석 → 실행 가능성 검증 → 샘플링 → 응답 조립" 네 단계로만 나눈다(동작 동일
    // 보장은 기존 LottoRecommendationServiceTest의 속성 기반 테스트가 검증).
    record RequestContext(int count, String strategy, List<Integer> locked, Set<Integer> excluded) {}

    RequestContext parseRequest(RecommendNumbersRequest request) {
        int count = request == null || request.count() == null ? 1 : request.count();

        List<Integer> locked = request == null || request.lockedNumbers() == null
                ? List.of()
                : lottoNumberCodec.normalizeSubset(request.lockedNumbers());
        if (locked.size() > MAX_LOCKED_NUMBERS) {
            throw fail(ApiErrorCode.TOO_MANY_LOCKED_NUMBERS,
                    "고정 번호는 최대 " + MAX_LOCKED_NUMBERS + "개까지 지정할 수 있습니다.");
        }

        Set<Integer> excluded = request == null || request.excludedNumbers() == null
                ? Set.of()
                : new HashSet<>(lottoNumberCodec.normalizeSubset(request.excludedNumbers()));

        if (!Collections.disjoint(locked, excluded)) {
            throw fail(ApiErrorCode.LOCKED_EXCLUDED_CONFLICT,
                    "고정 번호와 제외 번호가 겹칠 수 없습니다.");
        }

        String strategy = resolveStrategy(request);
        return new RequestContext(count, strategy, locked, excluded);
    }

    private String resolveStrategy(RecommendNumbersRequest request) {
        if (request != null && request.maximizePrize() != null) {
            // 구 필드명(maximizePrize) 사용량 추적 — 이 값이 계속 0에 머물면
            // 별도 변경에서 필드 자체를 제거해도 안전하다고 판단할 근거가 된다.
            meterRegistry.counter("kraft_recommend_legacy_field_used_total").increment();
        }
        String strategyParam = request == null ? null : request.strategy();
        if (strategyParam != null && !strategyParam.isBlank()) {
            String normalized = strategyParam.trim().toLowerCase();
            return switch (normalized) {
                case LottoRecommendationService.STRATEGY_RANDOM,
                        LottoRecommendationService.STRATEGY_BALANCED,
                        LottoRecommendationService.STRATEGY_REDUCE_SHARED_WINNER_RISK -> normalized;
                default -> throw fail(ApiErrorCode.INVALID_RECOMMENDATION_STRATEGY,
                        "지원하지 않는 추천 전략입니다: " + strategyParam);
            };
        }
        boolean reduceSharedWinnerRisk = request != null
                && Boolean.TRUE.equals(request.effectiveReduceSharedWinnerRisk());
        return reduceSharedWinnerRisk
                ? LottoRecommendationService.STRATEGY_REDUCE_SHARED_WINNER_RISK
                : LottoRecommendationService.STRATEGY_RANDOM;
    }

    void validateFeasibility(RequestContext ctx, RecommendationHistorySnapshotManager.HistorySnapshot snapshot) {
        if (45 - ctx.excluded().size() < 6) {
            throw fail(ApiErrorCode.TOO_MANY_EXCLUSIONS,
                    "제외 번호를 적용한 뒤에도 최소 6개 번호가 남아야 합니다.");
        }

        long available = 45L - ctx.excluded().size() - ctx.locked().size();
        int need = 6 - ctx.locked().size();
        long possible = combinations(available, need);
        long excludedMask = LottoBitmask.of(ctx.excluded());
        long lockedMask = LottoBitmask.of(ctx.locked());
        long compatibleHistoricalCount = snapshot.masks().stream()
                .filter(mask -> (mask & excludedMask) == 0L && (mask & lockedMask) == lockedMask)
                .count();
        long allowedPossible = possible - compatibleHistoricalCount;
        if (ctx.count() > allowedPossible) {
            throw fail(ApiErrorCode.INSUFFICIENT_UNIQUE_COMBINATIONS,
                    "요청한 조합 수(" + ctx.count() + ")가 역대 1등 조합을 제외하고 가능한 고유 조합 수("
                            + allowedPossible + ")를 초과합니다.");
        }
    }

    private static long combinations(long n, int k) {
        if (n < k) {
            return 0;
        }
        long result = 1;
        for (int i = 0; i < k; i++) {
            result = result * (n - i) / (i + 1);
        }
        return result;
    }

    private ApiException fail(ApiErrorCode errorCode, String message) {
        meterRegistry.counter("kraft_lotto_recommend_failures_total", "code", errorCode.name()).increment();
        return new ApiException(errorCode, message);
    }
}
