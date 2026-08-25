package com.kraft.ops;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.ZonedDateTime;

public record OpsSummaryResponse(
        String service,
        String timezone,
        String status,
        @Schema(nullable = true) Integer latestRound,
        @Schema(nullable = true) String latestDrawDate,
        ZonedDateTime checkedAt,
        boolean fresh
) {
}
