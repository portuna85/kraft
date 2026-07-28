package com.kraft.community.report;

import jakarta.validation.constraints.NotNull;

public record CreateReportRequest(
        @NotNull ReportTargetType targetType,
        @NotNull Long targetId,
        @NotNull ReportReason reason
) {
}
