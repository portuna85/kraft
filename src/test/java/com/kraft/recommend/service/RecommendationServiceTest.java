package com.kraft.recommend.service;

import com.kraft.recommend.domain.LottoNumbers;
import com.kraft.recommend.domain.RecommendationHistorySnapshot;
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

import static org.assertj.core.api.Assertions.assertThat;
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
    @DisplayName("성공하면 이력 기준 회차와 제외 정책을 담아 응답을 조립하고 이력 재검증을 호출한다")
    void assemblesResponseAndVerifiesHistory() {
        given(requestValidator.validate(new RecommendRequestDto(1))).willReturn(1);
        given(historyProvider.currentReadySnapshot()).willReturn(READY_SNAPSHOT);
        LottoNumbers combo = LottoNumbers.of(List.of(1, 2, 3, 4, 5, 6));
        given(candidateGenerator.generate(eq(1), eq(READY_SNAPSHOT))).willReturn(List.of(combo));

        RecommendResponseDto response = service.recommend(new RecommendRequestDto(1));

        assertThat(response.algorithmVersion()).isEqualTo("uniform-random-v1");
        assertThat(response.historyThroughRound()).isEqualTo(10);
        assertThat(response.historicalExclusionApplied()).isTrue();
        assertThat(response.exclusionPolicyVersion()).isEqualTo("historical-first-prize-v1");
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).position()).isEqualTo(1);
        assertThat(response.items().get(0).numbers()).containsExactly(1, 2, 3, 4, 5, 6);

        verify(historyProvider).verifyUnchanged(READY_SNAPSHOT);
    }
}
