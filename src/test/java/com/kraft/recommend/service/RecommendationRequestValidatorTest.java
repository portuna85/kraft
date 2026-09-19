package com.kraft.recommend.service;

import com.kraft.recommend.domain.RecommendationStrategy;
import com.kraft.recommend.domain.RecommendationValidationException;
import com.kraft.recommend.dto.RecommendRequestDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecommendationRequestValidatorTest {

    private final RecommendationRequestValidator validator = new RecommendationRequestValidator();

    @Test
    @DisplayName("본문이 없으면 count=1, strategy=random, 빈 고정·제외 목록으로 기본값이 적용된다")
    void emptyRequest_appliesDefaults() {
        NormalizedRecommendationRequest normalized = validator.validate(new RecommendRequestDto(null, null, null, null));

        assertThat(normalized.count()).isEqualTo(1);
        assertThat(normalized.strategy()).isEqualTo(RecommendationStrategy.RANDOM);
        assertThat(normalized.lockedNumbers()).isEmpty();
        assertThat(normalized.excludedNumbers()).isEmpty();
    }

    @Test
    @DisplayName("count 0과 11은 거절되고 1과 10은 통과한다")
    void countBoundary() {
        assertThatThrownBy(() -> validator.validate(new RecommendRequestDto(0, null, null, null)))
                .isInstanceOf(RecommendationValidationException.class)
                .satisfies(e -> assertThat(((RecommendationValidationException) e).getCode())
                        .isEqualTo("INVALID_RECOMMENDATION_REQUEST"));
        assertThatThrownBy(() -> validator.validate(new RecommendRequestDto(11, null, null, null)))
                .isInstanceOf(RecommendationValidationException.class);

        assertThat(validator.validate(new RecommendRequestDto(1, null, null, null)).count()).isEqualTo(1);
        assertThat(validator.validate(new RecommendRequestDto(10, null, null, null)).count()).isEqualTo(10);
    }

    @Test
    @DisplayName("고정 번호 6개는 TOO_MANY_LOCKED_NUMBERS로 거절된다")
    void tooManyLockedNumbers() {
        assertThatThrownBy(() -> validator.validate(
                new RecommendRequestDto(1, null, List.of(1, 2, 3, 4, 5, 6), null)))
                .isInstanceOf(RecommendationValidationException.class)
                .satisfies(e -> assertThat(((RecommendationValidationException) e).getCode())
                        .isEqualTo("TOO_MANY_LOCKED_NUMBERS"));
    }

    @Test
    @DisplayName("고정 번호 5개는 통과한다")
    void fiveLockedNumbers_isAllowed() {
        NormalizedRecommendationRequest normalized = validator.validate(
                new RecommendRequestDto(1, null, List.of(1, 2, 3, 4, 5), null));
        assertThat(normalized.lockedNumbers()).hasSize(5);
    }

    @Test
    @DisplayName("고정·제외 목록이 겹치면 LOCKED_EXCLUDED_CONFLICT")
    void lockedExcludedConflict() {
        assertThatThrownBy(() -> validator.validate(
                new RecommendRequestDto(1, null, List.of(1, 2), List.of(2, 3))))
                .isInstanceOf(RecommendationValidationException.class)
                .satisfies(e -> assertThat(((RecommendationValidationException) e).getCode())
                        .isEqualTo("LOCKED_EXCLUDED_CONFLICT"));
    }

    @Test
    @DisplayName("제외 40개는 TOO_MANY_EXCLUSIONS이고 39개는 통과한다")
    void exclusionBoundary() {
        List<Integer> forty = numbersUpTo(40);
        List<Integer> thirtyNine = numbersUpTo(39);

        assertThatThrownBy(() -> validator.validate(new RecommendRequestDto(1, null, null, forty)))
                .isInstanceOf(RecommendationValidationException.class)
                .satisfies(e -> assertThat(((RecommendationValidationException) e).getCode())
                        .isEqualTo("TOO_MANY_EXCLUSIONS"));

        NormalizedRecommendationRequest normalized =
                validator.validate(new RecommendRequestDto(1, null, null, thirtyNine));
        assertThat(normalized.excludedNumbers()).hasSize(39);
    }

    @Test
    @DisplayName("범위 밖·중복 번호는 INVALID_NUMBERS")
    void invalidNumbers() {
        assertThatThrownBy(() -> validator.validate(new RecommendRequestDto(1, null, List.of(0), null)))
                .isInstanceOf(RecommendationValidationException.class)
                .satisfies(e -> assertThat(((RecommendationValidationException) e).getCode())
                        .isEqualTo("INVALID_NUMBERS"));
        assertThatThrownBy(() -> validator.validate(new RecommendRequestDto(1, null, List.of(46), null)))
                .isInstanceOf(RecommendationValidationException.class);
        assertThatThrownBy(() -> validator.validate(new RecommendRequestDto(1, null, List.of(1, 1), null)))
                .isInstanceOf(RecommendationValidationException.class);
    }

    @Test
    @DisplayName("미지원 전략은 INVALID_RECOMMENDATION_STRATEGY")
    void unsupportedStrategy() {
        assertThatThrownBy(() -> validator.validate(new RecommendRequestDto(1, "maximizePrize", null, null)))
                .isInstanceOf(RecommendationValidationException.class)
                .satisfies(e -> assertThat(((RecommendationValidationException) e).getCode())
                        .isEqualTo("INVALID_RECOMMENDATION_STRATEGY"));
    }

    @Test
    @DisplayName("전략 문자열은 공백·대소문자를 정규화한 뒤 판정한다")
    void strategyIsNormalized() {
        NormalizedRecommendationRequest normalized =
                validator.validate(new RecommendRequestDto(1, "  BALANCED  ", null, null));
        assertThat(normalized.strategy()).isEqualTo(RecommendationStrategy.BALANCED);
    }

    private List<Integer> numbersUpTo(int count) {
        return java.util.stream.IntStream.rangeClosed(1, count).boxed().toList();
    }
}
