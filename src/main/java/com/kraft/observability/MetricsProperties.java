package com.kraft.observability;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code app.metrics.*} 설정(BE-43). 예전에는 {@link HealthReporter}가 {@code @Value} 필드 15개를 들고
 * 있었다. 기준값의 기본값은 여기 한 곳에 있다 — yml에 적지 않은 키는 이 값이 된다.
 *
 * @param enabled                         상태 점검 자체를 켤지 여부
 * @param avgResponseMs                   평균 응답 지연 상한(밀리초)
 * @param reportsPending                  신고는 사람이 처리한다. 하루치가 쌓이도록 아무도 보지 않았다면 알릴 일이다.
 * @param slowRequests                    평균이 정상이어도 이만큼 넘게 느린 요청이 있으면 알린다(O05)
 * @param sessionRevocationFailed         재시도를 모두 소진해 사람이 봐야 하는 세션 폐기 태스크 수 상한(O03)
 * @param imageDeleteBacklog              삭제 예약됐지만 아직 지우지 못한 이미지 파일 수 상한(O03)
 * @param recommendationHistoryStaleHours 추천 이력 검증 기준이 이만큼(시간) 지나면 알린다(O03). 기본값(200시간 ≈ 8.3일)은
 *                                        주 1회 수집이 한 번 밀려도 곧바로 울리지 않을 만큼의 여유다 — 실측 후 조정 대상이다.
 */
@ConfigurationProperties("app.metrics")
public record MetricsProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("20") int minRequests,
        @DefaultValue("0.1") double errorRate,
        @DefaultValue("0") long serverErrors,
        @DefaultValue("1000") long avgResponseMs,
        @DefaultValue("0.8") double poolUsage,
        @DefaultValue("1073741824") long diskFreeBytes,
        @DefaultValue("20") long mailPending,
        @DefaultValue("0") long mailFailed,
        @DefaultValue("20") long reportsPending,
        @DefaultValue("5") long slowRequests,
        @DefaultValue("0") long sessionRevocationFailed,
        @DefaultValue("200") long imageDeleteBacklog,
        @DefaultValue("200") long recommendationHistoryStaleHours) {

    /** yml 없이 쓰는 기본 설정(테스트에서 직접 조립할 때). */
    public static MetricsProperties defaults() {
        return new MetricsProperties(true, 20, 0.1, 0, 1000, 0.8, 1_073_741_824L, 20, 0, 20, 5, 0, 200, 200);
    }

    HealthThresholds thresholds() {
        return new HealthThresholds(minRequests, errorRate, serverErrors, avgResponseMs, poolUsage, diskFreeBytes,
                mailPending, mailFailed, reportsPending, slowRequests, sessionRevocationFailed, imageDeleteBacklog,
                recommendationHistoryStaleHours);
    }
}
