package com.kraft.common.web;

import com.kraft.Application;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = Application.class,
        properties = {
                "kraft.security.rate-limit-per-minute=1",
                "kraft.security.rate-limit-max-keys=100"
        }
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("공개 요청 제한 필터 테스트")
class PublicRateLimitFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    @DisplayName("BE-SEC-01: 서로 다른 임의 경로로 429를 유발해도 초과 메트릭 시계열 수가 고정된다")
    void rateLimitExceeded_arbitraryPaths_doNotIncreaseMeterCardinality() throws Exception {
        String ip = "10.9.9.42";
        // limit=1이므로 같은 IP의 두 번째 요청부터는 경로와 무관하게 429다. 첫 요청은
        // 한도 안이라 필터를 통과하고, 매핑되지 않은 경로라 404로 라우팅된다 —
        // 429 판정 자체가 라우팅보다 앞선 필터 단계에서 일어난다는 사실을 보여준다.
        mockMvc.perform(get("/api/v1/does-not-exist-0").with(remoteAddr(ip)))
                .andExpect(status().isNotFound());

        int distinctBogusPaths = 25;
        for (int i = 0; i < distinctBogusPaths; i++) {
            mockMvc.perform(get("/api/v1/does-not-exist-" + i + "-" + UUID.randomUUID())
                            .with(remoteAddr(ip)))
                    .andExpect(status().isTooManyRequests());
        }

        // surface = ops/community_write/public_api/unknown 4개 고정 태그뿐이어야 한다 —
        // 이전 구현(normalizePath 기반 path 태그)이었다면 distinctBogusPaths개의 별도
        // meter가 생겼을 것이다.
        long meterCount = meterRegistry.find("http.rate_limit.exceeded").meters().size();
        assertThat(meterCount).isEqualTo(4);
    }

    @Test
    @DisplayName("BE-SEC-02: rate_limit.decisions 메트릭이 outcome 4종으로 고정된다")
    void rateLimitDecisions_meterCardinalityIsFixed() throws Exception {
        String ip = "10.9.9.43";
        mockMvc.perform(get("/api/v1/stats/frequency").with(remoteAddr(ip)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/stats/frequency").with(remoteAddr(ip)))
                .andExpect(status().isTooManyRequests());

        long meterCount = meterRegistry.find("rate_limit.decisions").meters().size();
        assertThat(meterCount).isEqualTo(4);
    }

    @Test
    @DisplayName("BE-SEC-02: 정상 통과 요청은 rate_limit.decisions{outcome=allowed}를 증가시킨다")
    void allowedRequest_incrementsAllowedDecisionCounter() throws Exception {
        double before = counterValue("allowed");

        mockMvc.perform(get("/api/v1/stats/frequency").with(remoteAddr("10.9.9.44")))
                .andExpect(status().isOk());

        assertThat(counterValue("allowed")).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("BE-SEC-02: 429 응답은 rate_limit.decisions{outcome=limited}를 증가시킨다")
    void limitedRequest_incrementsLimitedDecisionCounter() throws Exception {
        String ip = "10.9.9.45";
        mockMvc.perform(get("/api/v1/stats/frequency").with(remoteAddr(ip)))
                .andExpect(status().isOk());
        double before = counterValue("limited");

        mockMvc.perform(get("/api/v1/stats/frequency").with(remoteAddr(ip)))
                .andExpect(status().isTooManyRequests());

        assertThat(counterValue("limited")).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("BE-SEC-02: trusted-proxy 대역 IP는 rate_limit.decisions{outcome=trusted_bypass}를 "
            + "증가시키고 X-RateLimit-* 헤더를 세팅하지 않는다")
    void trustedProxyIp_incrementsTrustedBypassDecisionCounter() throws Exception {
        // 테스트 프로파일의 kraft.security.trusted-proxy-cidr 기본값(application.yml)은
        // 172.28.0.0/16이다 — 이 대역 안 IP는 한도 검사 자체를 건너뛴다.
        double before = counterValue("trusted_bypass");

        mockMvc.perform(get("/api/v1/stats/frequency").with(remoteAddr("172.28.5.5")))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("X-RateLimit-Limit"))
                .andExpect(header().doesNotExist("X-RateLimit-Remaining"));

        assertThat(counterValue("trusted_bypass")).isEqualTo(before + 1);
    }

    private double counterValue(String outcome) {
        var counter = meterRegistry.find("rate_limit.decisions").tag("outcome", outcome).counter();
        return counter == null ? 0.0 : counter.count();
    }

    @Test
    @DisplayName("429 응답에 재시도 안내 헤더가 포함되는지 확인")
    void rateLimitExceeded_returnsRetryAfterHeader() throws Exception {
        mockMvc.perform(get("/api/v1/stats/frequency"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Limit", "1"))
                .andExpect(header().string("X-RateLimit-Remaining", "0"));

        mockMvc.perform(get("/api/v1/stats/frequency"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(header().string("X-RateLimit-Limit", "1"))
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(header().string("Content-Type", "application/json;charset=UTF-8"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.path").value("/api/v1/stats/frequency"));
    }

    @Test
    @DisplayName("preflight OPTIONS 요청은 레이트리밋 카운트를 소모하지 않는다")
    void preflightOptions_doesNotConsumeRateLimit() throws Exception {
        // limit=1이므로 OPTIONS가 카운트를 소모하면 뒤따르는 GET이 429가 된다
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(options("/api/v1/stats/frequency")
                            .with(remoteAddr("10.9.9.1"))
                            .header("Origin", "https://example.com")
                            .header("Access-Control-Request-Method", "GET"))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/v1/stats/frequency")
                        .with(remoteAddr("10.9.9.1")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("한도 초과 429 응답에도 CORS 헤더가 포함된다")
    void rateLimitExceeded_includesCorsHeaders() throws Exception {
        mockMvc.perform(get("/api/v1/stats/frequency")
                        .with(remoteAddr("10.9.9.2"))
                        .header("Origin", "https://example.com"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/stats/frequency")
                        .with(remoteAddr("10.9.9.2"))
                        .header("Origin", "https://example.com"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Access-Control-Allow-Origin"));
    }

    private static RequestPostProcessor remoteAddr(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
