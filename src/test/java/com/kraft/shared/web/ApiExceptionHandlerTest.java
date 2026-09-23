package com.kraft.shared.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link ApiExceptionHandler}가 {@code ResponseEntityExceptionHandler}를 상속한 뒤에도 전용
 * 핸들러가 없는 표준 MVC 예외가 catch-all(500)이 아니라 제 상태 코드로 응답하는지, 기존
 * 매핑은 그대로 유지되는지 확인한다(개선 보고서 OBS-04). 실제 도메인 컨트롤러 대신
 * {@link ApiExceptionHandlerTestController}가 이 예외들만 일으키는 더미 컨트롤러 역할을 한다 —
 * 보안 필터는 이 테스트의 관심사가 아니므로 꺼 둔다.
 */
@WebMvcTest(controllers = ApiExceptionHandlerTestController.class)
@AutoConfigureMockMvc(addFilters = false)
class ApiExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("OBS-04: 필수 쿼리 파라미터가 없으면 catch-all(500)이 아니라 400이다")
    void missingRequestParam_returns400() throws Exception {
        mockMvc.perform(get("/test/missing-param"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("OBS-04: 필수 멀티파트 파트가 없으면 400이다")
    void missingRequestPart_returns400() throws Exception {
        mockMvc.perform(multipart("/test/missing-part"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("OBS-04: 지원하지 않는 HTTP 메서드는 405다")
    void unsupportedMethod_returns405() throws Exception {
        mockMvc.perform(post("/test/method-not-allowed"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("OBS-04: 지원하지 않는 Content-Type은 415다")
    void unsupportedMediaType_returns415() throws Exception {
        mockMvc.perform(post("/test/media-type").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    @DisplayName("ResponseStatusException은 지정한 상태 코드와 메시지를 그대로 유지한다")
    void responseStatusException_keepsItsOwnStatus() throws Exception {
        mockMvc.perform(get("/test/response-status"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("찾을 수 없습니다."));
    }

    @Test
    @DisplayName("기존 IllegalArgumentException 매핑은 상속 후에도 그대로 400이다")
    void existingIllegalArgumentMapping_stillReturns400() throws Exception {
        mockMvc.perform(post("/test/illegal-argument"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("잘못된 요청입니다."));
    }
}
