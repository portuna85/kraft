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

    @Test
    @DisplayName("A-SEC-05: 한글이 없는 IllegalArgumentException은 400 대신 500 + 일반 문구다")
    void illegalArgumentWithoutKoreanMessage_returns500() throws Exception {
        mockMvc.perform(post("/test/illegal-argument-non-korean"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("서버 내부 오류가 발생했습니다."));
    }

    @Test
    @DisplayName("A-BE-12: 디스크 저장 실패(StorageException)는 400이 아니라 500이다")
    void storageException_returns500() throws Exception {
        mockMvc.perform(post("/test/storage-failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("서버 내부 오류가 발생했습니다."));
    }

    @Test
    @DisplayName("A-SEC-05: AccessDeniedException 메시지 끝의 내부 id는 응답에서 잘린다")
    void accessDenied_stripsTrailingInternalId() throws Exception {
        mockMvc.perform(post("/test/access-denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("작성자 본인 또는 관리자만 수정·삭제할 수 있습니다."));
    }

    @Test
    @DisplayName("A-BE-07: 검증 실패는 필드명 없는 detail과 errors[] 배열을 함께 준다")
    void validationFailure_returnsDetailAndFieldErrors() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"content\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("title"))))
                .andExpect(jsonPath("$.errors", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.errors[*].field", org.hamcrest.Matchers.containsInAnyOrder("title", "content")))
                .andExpect(jsonPath("$.errors[*].message",
                        org.hamcrest.Matchers.containsInAnyOrder("제목은 필수입니다.", "내용은 필수입니다.")));
    }

    private static org.springframework.dao.DataIntegrityViolationException violation(
            org.hibernate.exception.ConstraintViolationException.ConstraintKind kind) {
        return new org.springframework.dao.DataIntegrityViolationException("x",
                new org.hibernate.exception.ConstraintViolationException("x", new java.sql.SQLException("x"), kind, "c"));
    }

    @Test
    @DisplayName("P2-3: 유니크 위반은 409 '이미 사용 중', FK 위반은 409 '대상이 삭제·변경됨', NOT NULL은 500")
    void dataIntegrityViolation_isMappedByKind() {
        ApiExceptionHandler handler = new ApiExceptionHandler();

        var unique = handler.handleDataIntegrityViolation(violation(
                org.hibernate.exception.ConstraintViolationException.ConstraintKind.UNIQUE));
        var fk = handler.handleDataIntegrityViolation(violation(
                org.hibernate.exception.ConstraintViolationException.ConstraintKind.FOREIGN_KEY));
        var notNull = handler.handleDataIntegrityViolation(violation(
                org.hibernate.exception.ConstraintViolationException.ConstraintKind.NOT_NULL));
        var unknown = handler.handleDataIntegrityViolation(
                new org.springframework.dao.DataIntegrityViolationException("no hibernate cause"));

        org.assertj.core.api.Assertions.assertThat(unique.getStatus()).isEqualTo(409);
        org.assertj.core.api.Assertions.assertThat(unique.getDetail()).contains("이미 사용 중");
        org.assertj.core.api.Assertions.assertThat(fk.getStatus()).isEqualTo(409);
        org.assertj.core.api.Assertions.assertThat(fk.getDetail()).contains("삭제되었거나 변경");
        org.assertj.core.api.Assertions.assertThat(notNull.getStatus()).isEqualTo(500);
        org.assertj.core.api.Assertions.assertThat(unknown.getStatus()).isEqualTo(409);
    }
}
