package com.kraft;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("전역 예외 핸들러 — 파라미터 오류 400 변환 회귀 테스트")
class GlobalErrorHandlingIntegrationTest extends BaseApiIntegrationTest {

    @Test
    @DisplayName("필수 쿼리 파라미터 누락 시 400 오류를 반환한다")
    void missingRequiredQueryParam_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/numbers/check"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("MISSING_PARAMETER")));
    }

    @Test
    @DisplayName("쿼리 파라미터 타입 불일치 시 400 오류를 반환한다")
    void queryParamTypeMismatch_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/numbers/check").param("numbers", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_PARAMETER_TYPE")));
    }

    @Test
    @DisplayName("경로 변수 타입 불일치 시 400 오류를 반환한다")
    void pathVariableTypeMismatch_returns400() throws Exception {
        mockMvc.perform(post("/ops/collect/abc")
                        .header("X-Ops-Token", "test-ops-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_PARAMETER_TYPE")));
    }

    @Test
    @DisplayName("지원되지 않는 HTTP 메서드 요청 시 500이 아닌 405 오류를 반환한다")
    void unsupportedHttpMethod_returns405NotInternalServerError() throws Exception {
        mockMvc.perform(delete("/api/v1/rounds/latest"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"))
                .andExpect(jsonPath("$.code", is("METHOD_NOT_ALLOWED")));
    }

    @Test
    @DisplayName("지원되지 않는 Accept 헤더 요청 시 500이 아닌 406 오류를 반환한다")
    void unacceptableMediaType_returns406NotInternalServerError() throws Exception {
        mockMvc.perform(get("/api/v1/rounds/latest").accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable());
    }

    @Test
    @DisplayName("BE-API-01: 오류 응답 본문의 requestId가 X-Request-Id 응답 헤더와 일치한다")
    void errorResponse_includesRequestIdMatchingHeader() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/numbers/check"))
                .andExpect(status().isBadRequest())
                .andReturn();

        String headerRequestId = result.getResponse().getHeader("X-Request-Id");
        assertThat(headerRequestId).isNotBlank();
        JsonNode body = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("requestId").asText()).isEqualTo(headerRequestId);
    }
}
