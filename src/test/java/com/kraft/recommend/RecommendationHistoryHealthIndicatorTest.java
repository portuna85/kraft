package com.kraft.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

@ExtendWith(MockitoExtension.class)
class RecommendationHistoryHealthIndicatorTest {

    @Mock
    private LottoRecommendationService recommendationService;

    @Test
    void health_whenHistoryIsComplete_reportsUpWithoutNullDetails() {
        when(recommendationService.cachedHistoryStatus()).thenReturn(
                new LottoRecommendationService.HistoryStatus(true, 29L, 29L, 1234, 1234, null));
        when(recommendationService.historyLoadedAt()).thenReturn(Instant.now().minusSeconds(5));

        Health health = new RecommendationHistoryHealthIndicator(recommendationService).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails())
                .containsEntry("firstMissingRound", 0)
                .containsEntry("snapshotVersion", 29L)
                .containsEntry("databaseVersion", 29L);
    }

    @Test
    void health_whenHistoryHasGap_reportsDownWithMissingRound() {
        when(recommendationService.cachedHistoryStatus()).thenReturn(
                new LottoRecommendationService.HistoryStatus(false, 29L, 29L, 1233, 1234, 700));
        when(recommendationService.historyLoadedAt()).thenReturn(Instant.now().minusSeconds(5));

        Health health = new RecommendationHistoryHealthIndicator(recommendationService).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("firstMissingRound", 700);
    }
}
