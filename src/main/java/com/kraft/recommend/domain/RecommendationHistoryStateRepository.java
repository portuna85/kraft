package com.kraft.recommend.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;

public interface RecommendationHistoryStateRepository extends JpaRepository<RecommendationHistoryState, Integer> {

    /**
     * 검증 기준 메타데이터만 갱신한다. {@code version}은 절대 이 쿼리로 건드리지 않는다 —
     * V20 트리거가 {@code recommendation_winning_draws}의 DML로만 증가시키는 값이라, 엔티티를
     * 로드해 통째로 저장(save)하면 트리거가 이미 올려둔 값을 메모리에 캐시된 옛 값으로
     * 덮어써 버릴 수 있다(HIST-04/05). 그래서 필요한 컬럼만 직접 UPDATE한다.
     * <p>
     * {@code flushAutomatically = true}가 반드시 있어야 한다 — 이 쿼리는 JPQL을 거치지 않고
     * 즉시 실행되는 벌크 UPDATE라, 같은 트랜잭션에서 먼저 반영한 {@code WinningDraw}의
     * INSERT/변경이 아직 플러시되지 않은 상태로 남아 있으면 뒤이은 {@code clearAutomatically}가
     * 그 미반영 변경을 조용히 버린다(로컬 실측으로 확인 — RecommendationHistoryImporter가
     * 이 메서드를 항상 draws 반영 다음에 호출하는 이유이기도 하다).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RecommendationHistoryState s "
            + "SET s.verifiedThroughRound = :verifiedThroughRound, "
            + "s.sourceReference = :sourceReference, "
            + "s.verifiedAt = :verifiedAt "
            + "WHERE s.id = 1")
    void updateVerificationMetadata(Integer verifiedThroughRound, String sourceReference, LocalDateTime verifiedAt);
}
