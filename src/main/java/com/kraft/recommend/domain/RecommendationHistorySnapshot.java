package com.kraft.recommend.domain;

import java.time.Instant;
import java.util.Set;

/**
 * 특정 시점에 읽은 당첨 이력의 불변 스냅샷. {@code RecommendationHistoryProvider}가
 * {@code volatile} 참조로 통째로 교체한다(01문서 6절). 하나의 요청은 반드시 같은 스냅샷
 * 참조만 사용해야 한다.
 */
public record RecommendationHistorySnapshot(
        Set<Long> winningMasks,
        int roundCount,
        int maxRound,
        int verifiedThroughRound,
        long version,
        Instant loadedAt
) {

    /**
     * HIST-02/HIST-03: 1회부터 검증 기준 회차까지 누락 없이 이어져야 하고, 이력이 비어 있지
     * 않아야 한다. round_count는 DB에 실제로 존재하는 회차 수이며, verifiedThroughRound와
     * 일치해야 "누락 없음"을 뜻한다(연속성은 Importer가 반영 시점에 보장하고, 여기서는 그
     * 결과인 카운트 일치로 재확인한다).
     */
    public boolean isReady() {
        return roundCount > 0
                && verifiedThroughRound > 0
                && roundCount == verifiedThroughRound
                && maxRound == verifiedThroughRound;
    }
}
