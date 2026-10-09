package com.kraft.recommend.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;

public interface RecommendationHistoryStateRepository extends JpaRepository<RecommendationHistoryState, Integer> {

    /**
     * 검증 기준 메타데이터를 갱신하고 {@code version}도 올린다. V20 트리거는 {@code recommendation_winning_draws}의 DML에만
     * 걸려 있어, 회차 변경 없는 재검증은 트리거만으로는 버전이 오르지 않는다. 필요한 컬럼만 직접 UPDATE해 동시 갱신 때 캐시된
     * 옛 값으로 덮어쓰지 않는다.
     * <p>
     * {@code :verifiedThroughRound >= s.verifiedThroughRound} 조건이 있어, 늦게 끝난 백필이 더 앞서 나간 자동 수집의 검증
     * 구간을 되돌리지 못한다(구간을 줄이는 정정은 별도 운영 명령). 반환값 0은 이 조건에 걸려 바뀐 게 없다는 뜻이다.
     * <p>
     * {@code flushAutomatically = true}가 필수다 — 벌크 UPDATE 뒤 {@code clearAutomatically}가 아직 플러시되지 않은
     * {@code WinningDraw} 변경을 조용히 버린다(그래서 Importer는 항상 회차 반영 뒤에 이 메서드를 부른다).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RecommendationHistoryState s "
            + "SET s.verifiedThroughRound = :verifiedThroughRound, "
            + "s.sourceReference = :sourceReference, "
            + "s.verifiedAt = :verifiedAt, "
            + "s.version = s.version + 1 "
            + "WHERE s.id = 1 AND :verifiedThroughRound >= s.verifiedThroughRound")
    int updateVerificationMetadata(Integer verifiedThroughRound, String sourceReference, LocalDateTime verifiedAt);
}
