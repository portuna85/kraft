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
        name = "UK_REPORT_REPORTER_TARGET", columnNames = {"reporter_id", "target_type", "target_id"}))
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
     * 두 관리자가 같은 신고를 동시에 resolve/reject할 때 나중에 flush되는 쪽이 앞선 처리를
     * 조용히 덮어쓰지 않도록 한다(B11). {@code User.version}과 같은 목적이다.
     */
    @Version
    private long version;

    @Builder
    public Report(User reporter, ReportTargetType targetType, Long targetId, ReportReason reason, String detail) {
        this.reporter = reporter;
        this.targetType = targetType;
        this.targetId = targetId;
        this.reason = reason;
        this.detail = detail;
        this.status = ReportStatus.PENDING;
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

    private void markHandled(ReportStatus handledStatus, User admin) {
        this.status = handledStatus;
        this.handledBy = admin;
        this.handledAt = LocalDateTime.now();
    }
}
