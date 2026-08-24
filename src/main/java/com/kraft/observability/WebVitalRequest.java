package com.kraft.observability;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 프론트 web/src/app/api/vitals/route.ts가 검증하는 필드와 같은 값 집합을 서버에서
 * 다시 강제한다 — web 컨테이너가 손상되거나 이 endpoint가 직접 노출되는 경우까지
 * 방어한다(defense in depth). {@code release}는 의도적으로 받지 않는다 —
 * WebObservabilityMetrics의 클래스 주석 참고.
 */
public record WebVitalRequest(
        @NotBlank @Pattern(regexp = "LCP|INP|CLS") String name,
        @NotNull @DecimalMin("0") Double value,
        @NotBlank @Pattern(regexp = "good|needs-improvement|poor") String rating,
        @NotBlank @Size(max = 200) String route,
        @NotBlank @Pattern(regexp = "mobile|tablet|desktop") String deviceClass,
        @NotBlank @Pattern(regexp = "compact|desktop-nav") String layoutClass
) {
}
