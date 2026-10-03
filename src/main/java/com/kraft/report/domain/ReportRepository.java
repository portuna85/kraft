package com.kraft.report.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {

    /**
     * 관리자 화면의 기본 목록. 신고자를 함께 읽어 목록을 그리는 동안 건마다 추가 질의가
     * 나가지 않게 한다.
     */
    @EntityGraph(attributePaths = "reporter")
    Page<Report> findByStatusOrderByIdAsc(ReportStatus status, Pageable pageable);

    boolean existsByReporterIdAndTargetTypeAndTargetId(
            Long reporterId, ReportTargetType targetType, Long targetId);

    /** 한 대상에 쌓인 신고를 함께 처리할 때 쓴다. 지운 글의 다른 신고가 목록에 남으면 안 된다. */
    List<Report> findByTargetTypeAndTargetIdAndStatus(
            ReportTargetType targetType, Long targetId, ReportStatus status);

    /**
     * 게시글·최상위 댓글을 지우면 그 아래 댓글·답글도 함께 지워진다(cascade) — 그 자식들에
     * 걸린 대기 신고도 같이 처리해야 한다(BE-16). 그렇지 않으면 대상이 이미 사라졌는데도
     * PENDING으로 남아 {@code reports-pending} 알림만 부풀린다.
     */
    List<Report> findByTargetTypeAndTargetIdInAndStatus(
            ReportTargetType targetType, List<Long> targetIds, ReportStatus status);

    long countByStatus(ReportStatus status);

    /**
     * 처리 완료(대기 중이 아닌) 신고 중 스냅샷이 아직 남은 것의 id를 보관기간 정리 작업이
     * {@code pageable} 크기만큼만 읽는다(개선 보고서 A-SEC-07, {@code ReportSnapshotPurger}).
     * 예전에는 대상 엔티티를 제한 없이 전부 올려 한 트랜잭션에서 하나씩 갱신했다(BE-10).
     * 스냅샷이 이미 비어 있는 행은 매번 다시 훑지 않도록 {@code targetContentSnapshot IS NOT NULL}로 거른다.
     */
    @Query("SELECT r.id FROM Report r WHERE r.status <> com.kraft.report.domain.ReportStatus.PENDING "
            + "AND r.targetContentSnapshot IS NOT NULL AND r.handledAt < :threshold ORDER BY r.id")
    List<Long> findHandledIdsWithSnapshotOlderThan(@Param("threshold") LocalDateTime threshold, Pageable pageable);

    /**
     * 주어진 신고들의 제목·본문 스냅샷을 한 문장으로 비운다. 엔티티를 읽지 않으므로 낙관적 락
     * 버전·감사 시각은 바뀌지 않는다 — 처리가 끝난 신고라 더 편집될 일이 없고, 비우는 것이
     * 정리 작업의 목적이다. 자체 트랜잭션이라 묶음마다 짧게 커밋된다.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Report r SET r.targetTitleSnapshot = null, r.targetContentSnapshot = null WHERE r.id IN :ids")
    int clearSnapshotsByIdIn(@Param("ids") List<Long> ids);
}
