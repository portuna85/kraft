package com.kraft.community.report;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "community_reports")
public class CommunityReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reporter_user_id", nullable = false)
    private Long reporterUserId;

    @Column(name = "target_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ReportTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "reason", nullable = false, length = 40)
    @Enumerated(EnumType.STRING)
    private ReportReason reason;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected CommunityReport() {
    }

    public CommunityReport(Long reporterUserId, ReportTargetType targetType, Long targetId, ReportReason reason,
                            OffsetDateTime createdAt) {
        this.reporterUserId = reporterUserId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getReporterUserId() {
        return reporterUserId;
    }

    public ReportTargetType getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public ReportReason getReason() {
        return reason;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
