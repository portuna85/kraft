package com.kraft.observability;

import com.kraft.common.config.WebObservabilityProperties;
import com.kraft.common.error.ApiErrorCode;
import com.kraft.common.error.ApiException;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * OBS-WEB-01(docs/improvement.md): web 컨테이너는 프로덕션에서 app 네트워크에만
 * 있고 Prometheus가 있는 monitoring 네트워크와는 의도적으로 격리돼 있어(둘 다
 * internal: true), Prometheus가 web을 직접 스크랩할 수 없다. 그래서 web이 이
 * endpoint로 vitals/CSP/client-error 이벤트를 push하고, backend가 Micrometer로
 * 집계해 기존 {@code /actuator/prometheus}로 노출한다 — 새 Prometheus job도 새
 * 네트워크 배선도 필요 없다.
 *
 * <p>인증은 OpsTokenFilter와 같은 timing-safe 비교, 미설정=fail-closed. 이 endpoint는
 * DispatcherServlet 이후 컨트롤러 단계에서 인증하므로(OpsTokenFilter처럼 서블릿
 * 필터 이전 단계가 아니다) 실패 응답이 일반 {@code ApiException} → GlobalExceptionHandler
 * 경로를 그대로 탄다.
 */
@RestController
@RequestMapping("/api/v1/observability")
public class WebObservabilityController {

    static final String SECRET_HEADER = "X-Web-Observability-Secret";

    private final WebObservabilityProperties properties;
    private final WebObservabilityMetrics metrics;

    public WebObservabilityController(WebObservabilityProperties properties, WebObservabilityMetrics metrics) {
        this.properties = properties;
        this.metrics = metrics;
    }

    @PostMapping("/web-vitals")
    public ResponseEntity<Void> vitals(
            @RequestHeader(value = SECRET_HEADER, required = false) String secret,
            @Valid @RequestBody WebVitalRequest request) {
        requireValidSecret(secret);

        WebObservabilityMetrics.VitalMetric metric = WebObservabilityMetrics.VitalMetric.parse(request.name());
        WebObservabilityMetrics.VitalRating rating = WebObservabilityMetrics.VitalRating.parse(request.rating());
        WebObservabilityMetrics.DeviceClass deviceClass =
                WebObservabilityMetrics.DeviceClass.parse(request.deviceClass());
        WebObservabilityMetrics.LayoutClass layoutClass =
                WebObservabilityMetrics.LayoutClass.parse(request.layoutClass());
        // @Pattern이 이미 이 네 필드의 값 집합을 강제하므로 이론상 도달하지 않는다 —
        // enum과 @Pattern 정의가 어긋나는 미래의 실수를 조용히 넘기지 않는 안전망이다.
        if (metric == null || rating == null || deviceClass == null || layoutClass == null) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "vitals 값 집합과 enum 정의가 어긋남");
        }

        metrics.recordVital(metric, rating, deviceClass, layoutClass, RouteBucket.of(request.route()),
                request.value());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/csp-violations")
    public ResponseEntity<Void> cspViolations(
            @RequestHeader(value = SECRET_HEADER, required = false) String secret,
            @Valid @RequestBody CspViolationRequest request) {
        requireValidSecret(secret);

        metrics.recordCspViolation(CspDirective.of(request.violatedDirective()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/client-errors")
    public ResponseEntity<Void> clientErrors(
            @RequestHeader(value = SECRET_HEADER, required = false) String secret,
            @Valid @RequestBody ClientErrorRequest request) {
        requireValidSecret(secret);

        metrics.recordClientError(RouteBucket.of(request.route()));
        return ResponseEntity.noContent().build();
    }

    private void requireValidSecret(String provided) {
        String expected = properties.secret();
        if (expected == null || expected.isBlank()) {
            throw new ApiException(ApiErrorCode.OBSERVABILITY_UNAUTHORIZED, "관측 이벤트 수집 시크릿이 설정되지 않았습니다.");
        }
        if (provided == null || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(ApiErrorCode.OBSERVABILITY_UNAUTHORIZED, "관측 이벤트 수집 인증에 실패했습니다.");
        }
    }
}
