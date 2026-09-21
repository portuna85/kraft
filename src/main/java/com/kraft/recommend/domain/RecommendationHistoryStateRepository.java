package com.kraft.recommend.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;

public interface RecommendationHistoryStateRepository extends JpaRepository<RecommendationHistoryState, Integer> {

    /**
     * 검증 기준 메타데이터를 갱신하고 {@code version}도 함께 올린다. V20 트리거는
     * {@code recommendation_winning_draws}의 DML에만 걸려 있어, 회차 데이터 변경 없이
     * 검증 기준·출처만 재확인하는 갱신(예: 동일 이력 재검증)은 트리거만으로는 버전이 오르지
     * 않는다. 이 쿼리 자체가 {@code version}을 올려 HIST-04/05("메타데이터 변경도 버전 증가
     * 대상")를 만족시킨다. 엔티티를 로드해 통째로 저장(save)하지 않고 필요한 컬럼만 직접
     * UPDATE하는 이유는 동시 갱신 시 메모리에 캐시된 옛 값으로 다른 트랜잭션의 변경을
     * 덮어쓰지 않기 위함이다.
     * <p>
     * {@code WHERE}에 {@code :verifiedThroughRound >= s.verifiedThroughRound}를 둔다(B14) —
     * 이게 없으면 오래 걸린 백필이 그 사이 더 앞서 나간 자동 수집의 검증 구간을 뒤로 되돌릴
     * 수 있었다(예: 자동 수집이 이미 600회차까지 검증해 뒀는데, 그보다 먼저 시작된 500회차
     * 기준 백필이 늦게 끝나며 검증 구간을 500으로 덮어씀). 검증 구간을 의도적으로 줄이는
     * 것(정정)은 이 메서드의 책임이 아니다 — 별도 운영 명령으로 다룬다. 반환값(갱신된 행
     * 수)이 0이면 이 되돌림 조건에 걸려 아무것도 바뀌지 않았다는 뜻이다.
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
            + "s.verifiedAt = :verifiedAt, "
            + "s.version = s.version + 1 "
            + "WHERE s.id = 1 AND :verifiedThroughRound >= s.verifiedThroughRound")
    int updateVerificationMetadata(Integer verifiedThroughRound, String sourceReference, LocalDateTime verifiedAt);
}
