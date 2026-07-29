package com.kraft.recommend;

import java.time.Duration;
import java.time.Instant;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/** 추천 이력의 완전성과 DB/스냅샷 버전 일치를 readiness에 반영한다. */
@Component("recommendationHistory")
public class RecommendationHistoryHealthIndicator implements HealthIndicator {

    private final LottoRecommendationService recommendationService;

    public RecommendationHistoryHealthIndicator(LottoRecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @Override
    public Health health() {
        LottoRecommendationService.HistoryStatus status = recommendationService.historyStatus();
        Health.Builder builder = status.ready() ? Health.up() : Health.down();
        return builder
                .withDetail("snapshotVersion", status.snapshotVersion())
                .withDetail("databaseVersion", status.databaseVersion())
                .withDetail("roundCount", status.roundCount())
                .withDetail("historyThroughRound", status.historyThroughRound())
                // Health.Builder는 null detail을 허용하지 않아 이력이 완전한 정상 상태에서
                // readiness 자체가 500이 된다. 지표와 동일하게 0을 "누락 없음"으로 쓴다.
                .withDetail("firstMissingRound",
                        status.firstMissingRound() == null ? 0 : status.firstMissingRound())
                .withDetail("snapshotAgeSeconds", Duration.between(
                        recommendationService.historyLoadedAt(), Instant.now()).toSeconds())
                .build();
    }
}
