package com.kraft.operationlog;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

public record WinningNumberOperationLogResponse(
        long id,
        String operationType,
        String executionStatus,
        @Schema(nullable = true) Integer round,
        @Schema(nullable = true) String sourceDetail,
        @Schema(nullable = true) String message,
        @Schema(nullable = true) String requestId,
        OffsetDateTime createdAt
) {
    public static WinningNumberOperationLogResponse from(WinningNumberOperationLog log) {
        return new WinningNumberOperationLogResponse(
                log.getId(),
                log.getOperationType().name(),
                log.getExecutionStatus().name(),
                log.getRound(),
                log.getSourceDetail(),
                log.getMessage(),
                log.getRequestId(),
                log.getCreatedAt()
        );
    }
}
