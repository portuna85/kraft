package com.kraft.observability;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * message·digest는 받지 않는다 — WebObservabilityMetrics의 클래스 주석 참고
 * (카디널리티 무한대라 태그로도 로그로도 절대 쓰지 않는다). route만 개수 집계에 쓴다.
 */
public record ClientErrorRequest(
        @NotBlank @Size(max = 500) String route
) {
}
