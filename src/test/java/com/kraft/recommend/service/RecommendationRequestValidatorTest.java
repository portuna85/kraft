package com.kraft.recommend.service;

import com.kraft.recommend.domain.RecommendationValidationException;
import com.kraft.recommend.dto.RecommendRequestDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecommendationRequestValidatorTest {

    private final RecommendationRequestValidator validator = new RecommendationRequestValidator();

    @Test
    @DisplayName("본문이 없거나 count가 없으면 1개로 본다")
    void nullRequestAndNullCount_defaultToOne() {
        assertThat(validator.validate(null)).isEqualTo(1);
        assertThat(validator.validate(new RecommendRequestDto(null))).isEqualTo(1);
    }

    @Test
    @DisplayName("count 1과 10은 통과하고 0과 11은 INVALID_RECOMMENDATION_REQUEST로 거절된다")
    void countBoundaries() {
        assertThat(validator.validate(new RecommendRequestDto(1))).isEqualTo(1);
        assertThat(validator.validate(new RecommendRequestDto(10))).isEqualTo(10);

        for (int invalid : new int[] {0, 11}) {
            assertThatThrownBy(() -> validator.validate(new RecommendRequestDto(invalid)))
                    .isInstanceOf(RecommendationValidationException.class)
                    .satisfies(e -> assertThat(((RecommendationValidationException) e).getCode())
                            .isEqualTo("INVALID_RECOMMENDATION_REQUEST"));
        }
    }
}
