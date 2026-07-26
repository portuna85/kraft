package com.kraft.ops;

import com.kraft.Application;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B-02: kraft.ops.token이 비어 있는 상태(운영 토큰 미설정 — "의존성 불가"에 해당하는
 * 배포 오설정)에서도 공용 ApiErrorResponse 계약을 지키는지 별도 컨텍스트로 검증한다.
 */
@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "kraft.ops.token=")
@DisplayName("Ops 토큰 미설정(운영 API 비활성) 테스트")
class OpsTokenFilterDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("토큰이 설정되지 않았으면 503과 공용 ApiErrorResponse 계약(B-02)을 반환한다")
    void tokenNotConfigured_returnsServiceUnavailable() throws Exception {
        mockMvc.perform(get("/ops/summary").header("X-Ops-Token", "anything"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("OPS_DISABLED"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("Service Unavailable"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/ops/summary"));
    }
}
