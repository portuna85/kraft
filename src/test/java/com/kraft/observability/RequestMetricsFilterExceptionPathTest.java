package com.kraft.observability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * O05: {@link RequestMetricsFilterTest}의 "예외로 끝난 요청" 테스트는
 * {@code response.setStatus(...)}로 상태를 미리 세팅해 둔 뒤 필터만 단독으로 부른다 — 실제
 * 운영에서 최종 상태를 정하는 쪽(Spring의 {@code @RestControllerAdvice} 예외 변환)은 이 필터보다
 * "뒤"에서 도는데, 그 실제 변환 경로를 지나지 않으므로 어긋남이 있어도 이 단위 테스트만으로는
 * 드러나지 않는다.
 * <p>
 * 여기서는 전체 컨텍스트를 띄워 실제 컨트롤러가 예외를 던지고, {@code ApiExceptionHandler}가
 * 그것을 실제로 500으로 변환하는 진짜 경로를 MockMvc로 지나간 뒤, {@link RequestMetrics}가
 * 그 최종 상태를 정확히 집계했는지 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RequestMetricsFilterExceptionPathTest {

    /**
     * ApiExceptionHandler의 catch-all(Exception.class)이 실제로 어떤 응답을 만드는지 재현하기
     * 위한 테스트 전용 엔드포인트.
     * <p>
     * {@code @TestConfiguration}으로 한 단계 감싼다 — 두 가지를 직접 겪고 확인한 결과다.
     * (1) 테스트 클래스 안에 {@code @RestController}를 바로 두면 Spring Boot의
     * {@code TestTypeExcludeFilter}가 "테스트 클래스 소속"으로 보고 컴포넌트 스캔에서 아예
     * 뺀다 — 이 경우 엔드포인트가 등록되지 않아 404가 났다. (2) 반대로 이 {@code @TestConfiguration}
     * 안에 {@code @Bean} 메서드까지 함께 두면, {@code @SpringBootTest}의 자동 임포트(빈 등록)와
     * 패키지 컴포넌트 스캔(스테레오타입 등록)이 같은 컨트롤러를 두 번 등록해 "Ambiguous
     * mapping"으로 컨텍스트 로딩이 실패했다. 그래서 {@code @Bean} 없이 {@code @RestController}
     * 하나만 둔다 — {@code TestTypeExcludeFilter}의 제외 대상이 아니므로(테스트 클래스 자체가
     * 아니라 설정 클래스 소속) 컴포넌트 스캔으로 정확히 한 번만 등록된다.
     */
    @TestConfiguration
    static class ThrowingEndpointConfig {
        @RestController
        static class ThrowingController {
            @GetMapping("/test-observability/boom")
            public String boom() {
                throw new IllegalStateException("의도적으로 던진 예외 — O05 검증용");
            }
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RequestMetrics requestMetrics;

    @Test
    @DisplayName("실제 컨트롤러가 던진 예외가 ApiExceptionHandler를 거쳐 500이 되면 그 최종 상태로 집계된다")
    void unhandledControllerExceptionIsCountedAsTheFinalTranslatedStatus() throws Exception {
        requestMetrics.drain(); // 이전 테스트의 잔여 기록을 비운다.

        mockMvc.perform(get("/test-observability/boom")
                        .with(SecurityMockMvcRequestPostProcessors.user("tester@example.com").roles("USER")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isInternalServerError());

        RequestMetrics.Snapshot snapshot = requestMetrics.drain();
        assertThat(snapshot.requests()).isEqualTo(1);
        assertThat(snapshot.serverErrors())
                .as("실제 예외→상태 변환을 거친 뒤의 최종 상태(500)가 필터에 그대로 보여야 한다")
                .isEqualTo(1);
    }
}
