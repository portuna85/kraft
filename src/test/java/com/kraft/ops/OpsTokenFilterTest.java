package com.kraft.ops;

import com.kraft.Application;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Ops 토큰 필터 테스트")
class OpsTokenFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("올바른 X-Ops-Token이면 요청을 통과시킨다")
    void validToken_passesThrough() throws Exception {
        mockMvc.perform(get("/ops/summary").header("X-Ops-Token", "test-ops-token"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("토큰이 없으면 401과 공용 ApiErrorResponse 계약(B-02)을 반환한다")
    void missingToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/ops/summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("OPS_UNAUTHORIZED"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/ops/summary"));
    }

    @Test
    @DisplayName("틀린 토큰이면 401과 공용 ApiErrorResponse 계약(B-02)을 반환한다")
    void wrongToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/ops/summary").header("X-Ops-Token", "wrong-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("OPS_UNAUTHORIZED"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/ops/summary"));
    }

    @Test
    @DisplayName("/ops가 아닌 경로는 필터를 타지 않는다")
    void nonOpsPath_bypassesFilter() throws Exception {
        mockMvc.perform(get("/api/v1/stats/patterns"))
                .andExpect(status().isOk());
    }
}
