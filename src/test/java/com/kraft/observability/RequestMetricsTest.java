package com.kraft.observability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O05: 평균·최댓값만으로는 소수의 느린 요청이 다수의 빠른 요청에 묻힌다. 고정 임계값을 넘는
 * 요청 수를 별도로 세는 카운터가 정확한지 확인한다.
 */
class RequestMetricsTest {

    @Test
    @DisplayName("임계값 미만은 세지 않고, 임계값 이상만 slowRequests로 센다")
    void onlyRequestsAtOrAboveTheThresholdAreCountedAsSlow() {
        RequestMetrics metrics = new RequestMetrics(1000);

        metrics.record(200, 999);   // 임계값 미만 — 세지 않는다.
        metrics.record(200, 1000);  // 정확히 임계값 — 센다.
        metrics.record(200, 5000);  // 임계값 초과 — 센다.

        assertThat(metrics.drain().slowRequests()).isEqualTo(2);
    }

    @Test
    @DisplayName("드레인하면 다음 주기로 넘어가지 않는다")
    void slowRequestsResetsOnDrain() {
        RequestMetrics metrics = new RequestMetrics(1000);
        metrics.record(200, 2000);
        assertThat(metrics.drain().slowRequests()).isEqualTo(1);

        assertThat(metrics.drain().slowRequests()).isZero();
    }

    @Test
    @DisplayName("기본 생성자는 3000ms를 임계값으로 쓴다")
    void defaultConstructorUsesThreeSecondThreshold() {
        RequestMetrics metrics = new RequestMetrics();

        metrics.record(200, 2999);
        metrics.record(200, 3000);

        assertThat(metrics.drain().slowRequests()).isEqualTo(1);
    }
}
