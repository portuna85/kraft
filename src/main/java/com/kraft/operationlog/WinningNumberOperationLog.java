package com.kraft.operationlog;

import com.kraft.winningnumber.WinningNumberOperationType;
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
@Table(name = "winning_number_operation_logs")
public class WinningNumberOperationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = 30)
    private WinningNumberOperationType operationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_status", nullable = false, length = 20)
    private WinningNumberOperationStatus executionStatus;

    @Column(name = "round_no")
    private Integer round;

    @Column(name = "source_detail", length = 255)
    private String sourceDetail;

    @Column(name = "message", length = 500)
    private String message;

    @Column(name = "request_id", length = 100)
    private String requestId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected WinningNumberOperationLog() {
    }

    public WinningNumberOperationLog(WinningNumberOperationType operationType,
                                     WinningNumberOperationStatus executionStatus,
                                     Integer round,
                                     String sourceDetail,
                                     String message,
                                     String requestId,
                                     OffsetDateTime createdAt) {
        this.operationType = operationType;
        this.executionStatus = executionStatus;
        this.round = round;
        this.sourceDetail = sourceDetail;
        this.message = message;
        this.requestId = requestId;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public WinningNumberOperationType getOperationType() {
        return operationType;
    }

    public WinningNumberOperationStatus getExecutionStatus() {
        return executionStatus;
    }

    public Integer getRound() {
        return round;
    }

    public String getSourceDetail() {
        return sourceDetail;
    }

    public String getMessage() {
        return message;
    }

    public String getRequestId() {
        return requestId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
