package com.kraft.common.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@DisplayName("비동기 실행기 메트릭 통합 테스트")
class AsyncMetricsIntegrationTest {

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    @DisplayName("eventTaskExecutor의 active·queued 메트릭이 운영 대시보드 이름으로 등록된다")
    void eventExecutor_registersActiveAndQueuedMetrics() {
        assertThat(meterRegistry.find("executor.active")
                .tag("name", "eventTaskExecutor").gauge()).isNotNull();
        assertThat(meterRegistry.find("executor.queued")
                .tag("name", "eventTaskExecutor").gauge()).isNotNull();
    }
}
