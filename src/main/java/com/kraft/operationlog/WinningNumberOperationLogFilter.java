package com.kraft.operationlog;

import com.kraft.winningnumber.WinningNumberOperationType;
import java.time.OffsetDateTime;

public record WinningNumberOperationLogFilter(
        WinningNumberOperationType operationType,
        WinningNumberOperationStatus executionStatus,
        Integer round,
        OffsetDateTime createdFrom,
        OffsetDateTime createdToExclusive
) {
}
