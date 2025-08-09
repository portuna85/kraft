package com.kraft.book.common.error;

import java.time.Instant;
import java.util.Map;

public record ApiErrorResponse(
        String code,          // e.g. "VALIDATION_ERROR", "UNAUTHORIZED"
        String message,       // human readable summary
        Map<String, String> errors, // field -> message (optional)
        Instant timestamp     // server time
) {
    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(code, message, null, Instant.now());
    }
    public static ApiErrorResponse of(String code, String message, Map<String, String> errors) {
        return new ApiErrorResponse(code, message, errors, Instant.now());
    }
}
