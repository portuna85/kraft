package com.kraft.saved;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

public record SavedNumberResponse(
        long id,
        List<Integer> numbers,
        @Schema(nullable = true) String label,
        String source,
        OffsetDateTime createdAt
) {
}
