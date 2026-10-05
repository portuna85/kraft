package com.kraft.report.domain;

import com.kraft.shared.domain.BaseEntity;
import com.kraft.user.domain.User;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 신고 한 건.
 * <p>
 * 대상을 FK로 가리키지 않고 {@code targetType + targetId}로 두는 이유는 V10 주석에 있다.
 * 그래서 대상이 이미 사라진 신고가 있을 수 있고, 화면은 그 경우를 정상으로 다뤄야 한다
 * (처리하면 대상은 없어지고 기록은 남는다).
 */
@Getter
@Entity
@NoArgsConstructor
@Table(name = "reports", uniqueConstraints = @UniqueConstraint(
        name = "UK_REPORT_REPORTER_TARGET", columnNames = {"reporter_id", "target_type", "target_id"}),
        indexes = {
                // V10__reports.sql. 엔티티에 선언이 없어 ddl-auto: update로 만든 기존 DB에는
                // 이 인덱스들이 생기지 않았다(개선 보고서 O01). 관리자 목록(findByStatusOrderByIdAsc,
                // countByStatus)이 status로 걸러 id 순으로 훑는다.
                @Index(name = "IX_REPORTS_STATUS_ID", columnList = "status, id"),
                // findByTargetTypeAndTargetIdAndStatus가 대상 기준으로 훑는다.
                // UK_REPORT_REPORTER_TARGET은 reporter_id가 맨 앞이라 이 조회에는 못 쓰인다.
                @Index(name = "IX_REPORTS_TARGET", columnList = "target_type, target_id"),
                // V27__report_target_snapshot_and_deleted_status.sql. 스냅샷 보관기간 정리가
                // status로 거르고 handled_at 범위를 훑는다. 엔티티에 없으면 H2 테스트 스키마에는
                // 운영에 있는 이 인덱스가 빠진다(BE-27).
                @Index(name = "IX_REPORTS_STATUS_HANDLED_AT", columnList = "status, handled_at"),
        })
public class Report extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private ReportTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportReason reason;

    /** 신고자가 적은 사정. 비워 둘 수 있다. */
    @Column(length = 500)
    private String detail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handled_by_id")
    private User handledBy;

    @Column(name = "handled_at")
    private LocalDateTime handledAt;

    /**
     * 신고 접수 시점의 대상 작성자·제목·본문 스냅샷(전체 리뷰 2026-09-26 SEC-05 = 개선 보고서
     * A-SEC-07). 대상이 이미 사라져도(작성자가 스스로 지웠거나, 처리 중 지연) 관리자가 누구를
     * 정지해야 할지 판단할 최소한의 근거로 남는다 — {@code ReportService.resolve()}가 이
     * 필드를 실시간 조회의 대체 수단(fallback)으로만 쓴다. 처리 완료 후 일정 기간이 지나면
     * {@code ReportSnapshotPurger}가 벌크 UPDATE로 비운다(개인정보 보관기간 — {@code targetAuthor}는
     * 감사 기록으로 남긴다).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_author_id")
    private User targetAuthor;

    /** 댓글 신고면 항상 null이다 — 댓글에는 제목이 없다. */
    @Column(name = "target_title_snapshot")
    private String targetTitleSnapshot;

    @Column(name = "target_content_snapshot", length = 500)
    private String targetContentSnapshot;

    /**
     * 두 관리자가 같은 신고를 동시에 resolve/reject할 때 나중에 flush되는 쪽이 앞선 처리를
     * 조용히 덮어쓰지 않도록 한다(B11). {@code User.version}과 같은 목적이다.
     */
    @Version
    private long version;

    @Builder
    public Report(User reporter, ReportTargetType targetType, Long targetId, ReportReason reason, String detail,
                  User targetAuthor, String targetTitleSnapshot, String targetContentSnapshot) {
        this.reporter = reporter;
        this.targetType = targetType;
        this.targetId = targetId;
        this.reason = reason;
        this.detail = detail;
        this.status = ReportStatus.PENDING;
        this.targetAuthor = targetAuthor;
        this.targetTitleSnapshot = targetTitleSnapshot;
        this.targetContentSnapshot = targetContentSnapshot;
    }

    /** 신고를 받아들여 대상을 지웠다. */
    public void resolve(User admin) {
        markHandled(ReportStatus.RESOLVED, admin);
    }

    /** 문제가 없다고 판단했다. 대상은 그대로 둔다. */
    public void reject(User admin) {
        markHandled(ReportStatus.REJECTED, admin);
    }

    public boolean isPending() {
        return status == ReportStatus.PENDING;
    }

    /**
     * 관리자가 판단하기 전에 작성자 본인이 대상을 지워 사라졌다(A-BE-01). 호출자
     * ({@code ReportService})가 {@code PENDING}인 것만 걸러 넘기므로 여기서 다시 확인하지
     * 않는다 — {@code resolve}·{@code reject}도 같은 관례다.
     */
    public void closeAsTargetDeleted() {
        this.status = ReportStatus.TARGET_DELETED;
        this.handledAt = LocalDateTime.now();
    }

    private void markHandled(ReportStatus handledStatus, User admin) {
        this.status = handledStatus;
        this.handledBy = admin;
        this.handledAt = LocalDateTime.now();
    }
}
