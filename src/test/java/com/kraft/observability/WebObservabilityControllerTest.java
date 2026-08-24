package com.kraft.observability;

import com.kraft.Application;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OBS-WEB-01(docs/improvement.md): 성패 기준은 카디널리티 회귀 테스트다 — 임의
 * route/message 문자열을 대량으로 보내도 메트릭 시계열 수가 고정돼야 한다
 * (PublicRateLimitFilterTest의 BE-SEC-01 테스트와 같은 형태).
 */
@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("web 관측 이벤트 수집 endpoint 테스트")
class WebObservabilityControllerTest {

    private static final String SECRET = "test-web-observability-secret";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    @DisplayName("시크릿 헤더가 없으면 401을 반환하고 메트릭을 증가시키지 않는다")
    void missingSecret_rejectedWithoutRecording() throws Exception {
        long before = meterRegistry.find("web_client_errors_total").meters().size();

        mockMvc.perform(post("/api/v1/observability/client-errors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"route\":\"/\"}"))
                .andExpect(status().isUnauthorized());

        assertThat(meterRegistry.find("web_client_errors_total").meters().size()).isEqualTo(before);
    }

    @Test
    @DisplayName("시크릿이 틀리면 401을 반환한다")
    void wrongSecret_rejected() throws Exception {
        mockMvc.perform(post("/api/v1/observability/client-errors")
                        .header(WebObservabilityController.SECRET_HEADER, "wrong-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"route\":\"/\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("임의 route 문자열 25개를 보내도 client-error 메트릭 시계열이 최대 1개(other)만 늘어난다")
    void clientErrors_arbitraryRoutes_doNotIncreaseMeterCardinality() throws Exception {
        // 같은 Spring 컨텍스트를 공유하는 다른 테스트가 이미 recommend/saved 같은 다른
        // route 버킷의 meter를 만들어 뒀을 수 있다 — 절대 개수가 아니라 "25개의 서로
        // 다른 임의 문자열이 늘려도 되는 meter는 최대 1개(other)뿐"이라는 증가폭을 본다.
        long before = meterRegistry.find("web_client_errors_total").meters().size();

        int distinctBogusRoutes = 25;
        for (int i = 0; i < distinctBogusRoutes; i++) {
            String route = "/does-not-exist-" + i + "-" + UUID.randomUUID();
            mockMvc.perform(post("/api/v1/observability/client-errors")
                            .header(WebObservabilityController.SECRET_HEADER, SECRET)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"route\":\"" + route + "\"}"))
                    .andExpect(status().isNoContent());
        }

        // RouteBucket이 고정 enum이라 OTHER 하나로 다 묶여야 한다 — raw route 문자열을
        // 그대로 태그로 썼다면 distinctBogusRoutes개의 별도 meter가 늘었을 것이다.
        long after = meterRegistry.find("web_client_errors_total").meters().size();
        assertThat(after - before).isLessThanOrEqualTo(1);
    }

    @Test
    @DisplayName("알려진 route는 각자의 버킷으로 집계된다")
    void clientErrors_knownRoutes_bucketSeparately() throws Exception {
        mockMvc.perform(post("/api/v1/observability/client-errors")
                        .header(WebObservabilityController.SECRET_HEADER, SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"route\":\"/recommend\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/observability/client-errors")
                        .header(WebObservabilityController.SECRET_HEADER, SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"route\":\"/saved\"}"))
                .andExpect(status().isNoContent());

        assertThat(meterRegistry.find("web_client_errors_total").tag("route", "recommend").counter())
                .isNotNull();
        assertThat(meterRegistry.find("web_client_errors_total").tag("route", "saved").counter())
                .isNotNull();
    }

    @Test
    @DisplayName("web-vitals는 유효한 요청을 받아 web_vitals 요약에 기록한다")
    void vitals_validRequest_recorded() throws Exception {
        String body = "{"
                + "\"name\":\"LCP\",\"value\":1200.5,\"rating\":\"good\","
                + "\"route\":\"/\",\"deviceClass\":\"desktop\",\"layoutClass\":\"desktop-nav\"}";

        mockMvc.perform(post("/api/v1/observability/web-vitals")
                        .header(WebObservabilityController.SECRET_HEADER, SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNoContent());

        assertThat(meterRegistry.find("web_vitals").tag("metric", "lcp").summary()).isNotNull();
    }

    @Test
    @DisplayName("web-vitals는 잘못된 rating 값을 400으로 거부한다")
    void vitals_invalidRating_rejected() throws Exception {
        String body = "{"
                + "\"name\":\"LCP\",\"value\":1200.5,\"rating\":\"bogus\","
                + "\"route\":\"/\",\"deviceClass\":\"desktop\",\"layoutClass\":\"desktop-nav\"}";

        mockMvc.perform(post("/api/v1/observability/web-vitals")
                        .header(WebObservabilityController.SECRET_HEADER, SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("CSP 위반은 directive 값으로만 집계되고 알려지지 않은 값은 other로 묶인다")
    void cspViolations_unknownDirective_bucketedAsOther() throws Exception {
        mockMvc.perform(post("/api/v1/observability/csp-violations")
                        .header(WebObservabilityController.SECRET_HEADER, SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"violatedDirective\":\"totally-unknown-directive\"}"))
                .andExpect(status().isNoContent());

        assertThat(meterRegistry.find("web_csp_violations_total").tag("directive", "other").counter())
                .isNotNull();
    }

    @Test
    @DisplayName("OBS-WEB-01 검증: 기록한 메트릭이 실제 /actuator/prometheus 스크랩 출력에 나타난다")
    void recordedMetrics_appearInPrometheusScrapeOutput() throws Exception {
        mockMvc.perform(post("/api/v1/observability/client-errors")
                        .header(WebObservabilityController.SECRET_HEADER, SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"route\":\"/companion\"}"))
                .andExpect(status().isNoContent());

        // /actuator/prometheus는 trusted-proxy CIDR(기본 172.28.0.0/16)만 허용한다
        // (WebSecurityConfig) — Prometheus가 실제로 스크랩할 때와 같은 경로를 확인한다.
        mockMvc.perform(get("/actuator/prometheus").with(remoteAddr("172.28.0.5")))
                .andExpect(status().isOk())
                .andExpect(content -> {
                    String body = content.getResponse().getContentAsString();
                    assertThat(body).contains("web_client_errors_total");
                    assertThat(body).contains("route=\"companion\"");
                });
    }

    private static RequestPostProcessor remoteAddr(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
