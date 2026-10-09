package com.kraft.recommend.domain;

import java.time.Instant;
import java.util.Set;

/** 특정 시점에 읽은 당첨 이력의 불변 스냅샷. {@code RecommendationHistoryProvider}가 {@code volatile} 참조로 통째로 교체하며, 한 요청은 반드시 같은 스냅샷만 써야 한다. */
public record RecommendationHistorySnapshot(
        Set<Long> winningMasks,
        int roundCount,
        int maxRound,
        int verifiedThroughRound,
        long version,
        Instant loadedAt
) {

    /** {@code winningMasks}는 여러 요청이 공유하므로 경계에서 방어적으로 불변 집합으로 저장한다. */
    public RecommendationHistorySnapshot {
        winningMasks = Set.copyOf(winningMasks);
    }

    /** 1회부터 검증 기준 회차까지 누락 없이 이어지고 비어 있지 않은지 — round_count(실제 회차 수)가 verifiedThroughRound와 일치하면 "누락 없음"이다(연속성은 Importer가 반영 시점에 보장). */
    public boolean isReady() {
        return roundCount > 0
                && verifiedThroughRound > 0
                && roundCount == verifiedThroughRound
                && maxRound == verifiedThroughRound;
    }
}
