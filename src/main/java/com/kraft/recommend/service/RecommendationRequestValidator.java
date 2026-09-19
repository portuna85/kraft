package com.kraft.recommend.service;

import com.kraft.recommend.domain.LottoNumbers;
import com.kraft.recommend.domain.RecommendationStrategy;
import com.kraft.recommend.domain.RecommendationValidationException;
import com.kraft.recommend.dto.RecommendRequestDto;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * NUM-01~06(03문서 2절)을 검증하고 기본값을 적용한다. {@code @Valid}는 컨트롤러 경유
 * 요청에서만 동작하므로, 이 검증기는 서비스 경계에서 항상 다시 확인해 직접 호출 경로에도
 * 같은 불변식을 강제한다(02문서 4절).
 */
@Component
public class RecommendationRequestValidator {

    private static final int MAX_LOCKED = 5;
    private static final int MIN_REMAINING_AFTER_EXCLUSION = LottoNumbers.SIZE;

    public NormalizedRecommendationRequest validate(RecommendRequestDto request) {
        int count = normalizeCount(request == null ? null : request.count());
        RecommendationStrategy strategy = RecommendationStrategy.parse(request == null ? null : request.strategy())
                .orElseThrow(() -> new RecommendationValidationException(
                        "INVALID_RECOMMENDATION_STRATEGY", "지원하지 않는 전략입니다: " + request.strategy()));

        Set<Integer> locked = normalizeNumbers(request == null ? null : request.lockedNumbers());
        Set<Integer> excluded = normalizeNumbers(request == null ? null : request.excludedNumbers());

        if (locked.size() > MAX_LOCKED) {
            throw new RecommendationValidationException(
                    "TOO_MANY_LOCKED_NUMBERS", "고정 번호는 최대 " + MAX_LOCKED + "개까지 가능합니다.");
        }
        if (LottoNumbers.MAX - excluded.size() < MIN_REMAINING_AFTER_EXCLUSION) {
            throw new RecommendationValidationException(
                    "TOO_MANY_EXCLUSIONS", "제외 후 남는 번호가 6개 미만입니다.");
        }
        boolean conflicts = locked.stream().anyMatch(excluded::contains);
        if (conflicts) {
            throw new RecommendationValidationException(
                    "LOCKED_EXCLUDED_CONFLICT", "고정 번호와 제외 번호가 겹칠 수 없습니다.");
        }

        return new NormalizedRecommendationRequest(count, strategy, locked, excluded);
    }

    private int normalizeCount(Integer count) {
        if (count == null) {
            return 1;
        }
        if (count < 1 || count > 10) {
            throw new RecommendationValidationException(
                    "INVALID_RECOMMENDATION_REQUEST", "count는 1~10 사이여야 합니다.");
        }
        return count;
    }

    private Set<Integer> normalizeNumbers(List<Integer> raw) {
        if (raw == null || raw.isEmpty()) {
            return Set.of();
        }
        Set<Integer> result = new LinkedHashSet<>();
        for (Integer number : raw) {
            if (number == null || number < LottoNumbers.MIN || number > LottoNumbers.MAX) {
                throw new RecommendationValidationException(
                        "INVALID_NUMBERS", "번호는 1~45 범위의 정수여야 합니다.");
            }
            if (!result.add(number)) {
                throw new RecommendationValidationException(
                        "INVALID_NUMBERS", "목록에 중복된 번호가 있습니다: " + number);
            }
        }
        return result;
    }
}
