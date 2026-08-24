package com.kraft.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * OBS-WEB-01(docs/improvement.md): web 컨테이너가 push하는 Web Vitals·CSP 위반·
 * client render error를 Micrometer로 집계해 기존 {@code /actuator/prometheus}로
 * 노출한다.
 *
 * <p><b>카디널리티 설계</b>: {@code PublicRateLimitFilter}(BE-SEC-01)처럼 태그는
 * 전부 고정 enum에서만 나온다. 다만 그 필터는 태그 조합이 4×4개뿐이라 생성자에서
 * 전부 미리 등록(hoisting)했지만, 여기는 {@code metric×rating×device_class×
 * layout_class×route} 조합이 수백 개라 전부 미리 등록하는 건 실용적이지 않다 —
 * 대신 {@code Counter.builder(...).tags(...).register(registry)}를 요청마다
 * 호출한다. Micrometer의 {@code MeterRegistry}는 이름+태그 조합으로 내부적으로
 * 캐싱하므로 이 호출은 "매번 새로 등록"이 아니라 기존 meter를 찾아 재사용한다 —
 * 안전한 것은 태그 값이 전부 유한 enum에서만 나온다는 사실이지, 미리 등록했는지가
 * 아니다.
 *
 * <p><b>release를 태그로 쓰지 않는 이유</b>: 프론트 {@code /api/vitals}의 {@code
 * release} 필드는 브라우저가 보내는 자유 문자열(최대 100자)이다 — 정상 클라이언트는
 * 빌드 시점 상수를 보내지만, 임의 POST는 100자 이내 아무 문자열이나 보낼 수 있다.
 * 이걸 그대로 태그로 쓰면 BE-SEC-01이 고친 것과 같은 무제한 카디널리티 문제가
 * 재발한다 — 그래서 release는 집계에서 뺐다(route/rating 등 이미 검증된 enum
 * 차원만 남김).
 */
@Component
class WebObservabilityMetrics {

    private static final String VITALS_METRIC_NAME = "web_vitals";
    private static final String CSP_METRIC_NAME = "web_csp_violations_total";
    private static final String CLIENT_ERROR_METRIC_NAME = "web_client_errors_total";

    enum VitalMetric {
        LCP,
        INP,
        CLS;

        static VitalMetric parse(String raw) {
            for (VitalMetric metric : values()) {
                if (metric.name().equals(raw)) {
                    return metric;
                }
            }
            return null;
        }

        String tagValue() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    enum VitalRating {
        GOOD("good"),
        NEEDS_IMPROVEMENT("needs-improvement"),
        POOR("poor");

        private final String tagValue;

        VitalRating(String tagValue) {
            this.tagValue = tagValue;
        }

        static VitalRating parse(String raw) {
            for (VitalRating rating : values()) {
                if (rating.tagValue.equals(raw)) {
                    return rating;
                }
            }
            return null;
        }

        String tagValue() {
            return tagValue;
        }
    }

    enum DeviceClass {
        MOBILE,
        TABLET,
        DESKTOP;

        static DeviceClass parse(String raw) {
            for (DeviceClass deviceClass : values()) {
                if (deviceClass.name().toLowerCase(Locale.ROOT).equals(raw)) {
                    return deviceClass;
                }
            }
            return null;
        }

        String tagValue() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    enum LayoutClass {
        COMPACT("compact"),
        DESKTOP_NAV("desktop-nav");

        private final String tagValue;

        LayoutClass(String tagValue) {
            this.tagValue = tagValue;
        }

        static LayoutClass parse(String raw) {
            for (LayoutClass layoutClass : values()) {
                if (layoutClass.tagValue.equals(raw)) {
                    return layoutClass;
                }
            }
            return null;
        }

        String tagValue() {
            return tagValue;
        }
    }

    private final MeterRegistry meterRegistry;

    WebObservabilityMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    void recordVital(VitalMetric metric, VitalRating rating, DeviceClass deviceClass,
                     LayoutClass layoutClass, RouteBucket route, double value) {
        DistributionSummary.builder(VITALS_METRIC_NAME)
                .tag("metric", metric.tagValue())
                .tag("rating", rating.tagValue())
                .tag("device_class", deviceClass.tagValue())
                .tag("layout_class", layoutClass.tagValue())
                .tag("route", route.tagValue())
                .register(meterRegistry)
                .record(value);
    }

    void recordCspViolation(CspDirective directive) {
        Counter.builder(CSP_METRIC_NAME)
                .tag("directive", directive.tagValue())
                .register(meterRegistry)
                .increment();
    }

    void recordClientError(RouteBucket route) {
        Counter.builder(CLIENT_ERROR_METRIC_NAME)
                .tag("route", route.tagValue())
                .register(meterRegistry)
                .increment();
    }
}
