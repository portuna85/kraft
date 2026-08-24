package com.kraft.observability;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 프론트 csp-report/route.ts가 이미 브라우저 CSP report에서 4개 필드만 뽑아
 * 300자로 truncate했다 — 여기서는 메트릭에 쓰는 {@code violatedDirective}만 받는다.
 * documentUri/blockedUri/sourceFile은 메트릭에 쓰지 않으므로 전달받지 않는다.
 */
public record CspViolationRequest(
        @NotBlank @Size(max = 300) String violatedDirective
) {
}
