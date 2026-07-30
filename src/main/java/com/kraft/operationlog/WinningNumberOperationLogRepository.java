package com.kraft.operationlog;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WinningNumberOperationLogRepository extends JpaRepository<WinningNumberOperationLog, Long>,
        JpaSpecificationExecutor<WinningNumberOperationLog> {

    Page<WinningNumberOperationLog> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    // 공개 상태 페이지용: 수집 실패 또는 수동 보정만 "주목할 만한" 이력으로 본다.
    // 정상 자동 수집(EXTERNAL_COLLECT/SUCCESS)은 매주 발생하는 정상 동작이라 제외한다.
    @Query("SELECT l FROM WinningNumberOperationLog l "
            + "WHERE l.createdAt >= :since "
            + "AND (l.executionStatus = com.kraft.operationlog.WinningNumberOperationStatus.FAILURE "
            + "     OR l.operationType = com.kraft.winningnumber.WinningNumberOperationType.MANUAL_UPSERT) "
            + "ORDER BY l.createdAt DESC")
    List<WinningNumberOperationLog> findNotableSince(@Param("since") OffsetDateTime since);

    // 공개 인시던트 카드 집계용: 여러 회차의 해결 여부를 건별 exists 쿼리 대신 한 번에 조회한다.
    @Query("SELECT new com.kraft.operationlog.RoundLatestSuccess(l.round, MAX(l.createdAt)) "
            + "FROM WinningNumberOperationLog l "
            + "WHERE l.round IN :rounds "
            + "AND l.executionStatus = com.kraft.operationlog.WinningNumberOperationStatus.SUCCESS "
            + "GROUP BY l.round")
    List<RoundLatestSuccess> findLatestSuccessTimestampsForRounds(@Param("rounds") Collection<Integer> rounds);

    // 파생 delete는 대상 행을 전부 로드한 뒤 건별로 삭제한다 — 벌크 DELETE 한 번으로 대체한다.
    @Modifying
    @Query("delete from WinningNumberOperationLog l where l.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") OffsetDateTime cutoff);
}
