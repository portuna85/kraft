package com.kraft.observability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 배포 스크립트는 로그인 없이 {@code /readyz}를 찌른다 — 실제 보안 필터 체인과 DataSource로
 * 익명 접근·200·빈 본문을 확인한다(평가 보고서 2026-09-25 F04).
 */
@SpringBootTest
@AutoConfigureMockMvc
class HealthEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("익명 요청으로 healthz·readyz 모두 200, 본문 없음")
    void anonymousProbes_areOk() throws Exception {
        mockMvc.perform(get("/healthz")).andExpect(status().isOk()).andExpect(content().string(""));
        mockMvc.perform(get("/readyz")).andExpect(status().isOk()).andExpect(content().string(""));
    }
}
