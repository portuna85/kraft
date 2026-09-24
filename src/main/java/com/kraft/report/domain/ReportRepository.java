package com.kraft.report.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
