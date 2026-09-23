package com.kraft.recommend.service;

import com.kraft.recommend.domain.LottoBitmask;
import com.kraft.recommend.domain.LottoNumbers;
import com.kraft.recommend.domain.RecommendationHistorySnapshot;
import com.kraft.recommend.domain.RecommendationStrategy;
import com.kraft.recommend.domain.RecommendationValidationException;
import com.kraft.recommend.dto.RecommendRequestDto;
import com.kraft.recommend.dto.RecommendResponseDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock
    private RecommendationRequestValidator requestValidator;
    @Mock
    private RecommendationHistoryProvider historyProvider;
    @Mock
    private RecommendationCandidateGenerator candidateGenerator;

    @InjectMocks
    private RecommendationService service;

    private static final RecommendationHistorySnapshot READY_SNAPSHOT =
            new RecommendationHistorySnapshot(Set.of(), 10, 10, 10, 5L, Instant.now());

    @Test
    @DisplayName("random 전략 성공 시 score=null, explanationCodes=[]로 응답을 조립하고 이력 재검증을 호출한다")
    void random_assemblesResponseWithoutScore() {
        NormalizedRecommendationRequest normalized =
                new NormalizedRecommendationRequest(1, RecommendationStrategy.RANDOM, Set.of(), Set.of());
        given(requestValidator.validate(any())).willReturn(normalized);
        given(historyProvider.currentReadySnapshot()).willReturn(READY_SNAPSHOT);
        LottoNumbers combo = LottoNumbers.of(List.of(1, 2, 3, 4, 5, 6));
        given(candidateGenerator.generateRandom(eq(normalized), eq(READY_SNAPSHOT), anyLong())).willReturn(List.of(combo));

        RecommendResponseDto response = service.recommend(new RecommendRequestDto(1, "random", null, null));

        assertThat(response.strategy()).isEqualTo("random");
        assertThat(response.algorithmVersion()).isEqualTo("uniform-random-v1");
        assertThat(response.historyThroughRound()).isEqualTo(10);
        assertThat(response.historicalExclusionApplied()).isTrue();
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).score()).isNull();
        assertThat(response.items().get(0).explanationCodes()).isEmpty();
        assertThat(response.items().get(0).numbers()).containsExactly(1, 2, 3, 4, 5, 6);

        verify(historyProvider).verifyUnchanged(READY_SNAPSHOT);
    }

    @Test
    @DisplayName("수학적으로 가능한 조합 수가 부족하면 INSUFFICIENT_UNIQUE_COMBINATIONS로 즉시 거절한다")
    void insufficientCombinations_throwsValidationException() {
        // 제외 39개(7~45)로 자유 번호를 1~6 여섯 개만 남기고, 그 유일한 조합이 이미 과거
        // 당첨 조합이면 허용 가능 수는 0이 되어 count=1도 채울 수 없다.
        Set<Integer> excluded = IntStream.rangeClosed(7, 45).boxed().collect(Collectors.toSet());
        NormalizedRecommendationRequest normalized =
                new NormalizedRecommendationRequest(1, RecommendationStrategy.RANDOM, Set.of(), excluded);
        given(requestValidator.validate(any())).willReturn(normalized);

        long onlyPossibleMask = LottoBitmask.maskOf(List.of(1, 2, 3, 4, 5, 6));
        RecommendationHistorySnapshot snapshotWithOnlyComboUsed =
                new RecommendationHistorySnapshot(Set.of(onlyPossibleMask), 1, 1, 1, 1L, Instant.now());
        given(historyProvider.currentReadySnapshot()).willReturn(snapshotWithOnlyComboUsed);

        assertThatThrownBy(() -> service.recommend(new RecommendRequestDto(1, "random", null, excluded.stream().toList())))
                .isInstanceOf(RecommendationValidationException.class)
                .satisfies(e -> assertThat(((RecommendationValidationException) e).getCode())
                        .isEqualTo("INSUFFICIENT_UNIQUE_COMBINATIONS"));
    }
}
