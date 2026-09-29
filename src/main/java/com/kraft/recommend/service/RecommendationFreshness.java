package com.kraft.recommend.service;

import com.kraft.recommend.domain.RecommendationHistoryState;
import com.kraft.recommend.domain.RecommendationHistoryStateRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 추천 화면에 보여줄 "이력이 어디까지, 언제 확인됐는가"(P1-3). 제외 검증은 이 이력에 의존하므로
 * 사용자가 검증 기준을 알 수 있어야 한다. 지연 판정은 운영 경보와 같은 임계값
 * ({@code app.metrics.recommendation-history-stale-hours})을 쓴다.
 */
@Component
public class RecommendationFreshness {

    /**
     * @param verifiedThroughRound 검증이 끝난 최신 회차. 준비 전이면 {@code null}
     * @param verifiedAt           마지막으로 검증한 시각. 준비 전이면 {@code null}
     * @param ready                이력이 준비되어 있는가
     * @param stale                준비되어 있지만 임계 시간 넘게 갱신되지 않았는가
     */
    public record Status(Integer verifiedThroughRound, LocalDateTime verifiedAt, boolean ready, boolean stale) {
    }

    private final RecommendationHistoryStateRepository stateRepository;
    private final long staleHours;

    public RecommendationFreshness(RecommendationHistoryStateRepository stateRepository,
                                   @Value("${app.metrics.recommendation-history-stale-hours:200}") long staleHours) {
        this.stateRepository = stateRepository;
        this.staleHours = staleHours;
    }

    public Status current() {
        RecommendationHistoryState state = stateRepository.findById(1).orElse(null);
        if (state == null || state.getVerifiedThroughRound() == null || state.getVerifiedAt() == null) {
            return new Status(null, null, false, false);
        }
        boolean stale = Duration.between(state.getVerifiedAt(), LocalDateTime.now()).toHours() > staleHours;
        return new Status(state.getVerifiedThroughRound(), state.getVerifiedAt(), true, stale);
    }
}
