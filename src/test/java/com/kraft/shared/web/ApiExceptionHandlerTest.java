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
 * 매핑은 그대로 유지되는지 확인한다. 실제 도메인 컨트롤러 대신
 * {@link ApiExceptionHandlerTestController}가 이 예외들만 일으키는 더미 컨트롤러 역할을 한다 —
 * 보안 필터는 이 테스트의 관심사가 아니므로 꺼 둔다.
 */
@WebMvcTest(controllers = ApiExceptionHandlerTestController.class)
@AutoConfigureMockMvc(addFilters = false)
class ApiExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("필수 쿼리 파라미터가 없으면 catch-all(500)이 아니라 400이다")
    void missingRequestParam_returns400() throws Exception {
        mockMvc.perform(get("/test/missing-param"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("필수 멀티파트 파트가 없으면 400이다")
    void missingRequestPart_returns400() throws Exception {
        mockMvc.perform(multipart("/test/missing-part"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("지원하지 않는 HTTP 메서드는 405다")
    void unsupportedMethod_returns405() throws Exception {
        mockMvc.perform(post("/test/method-not-allowed"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("지원하지 않는 Content-Type은 415다")
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
    @DisplayName("BusinessValidationException은 400이다")
    void existingIllegalArgumentMapping_stillReturns400() throws Exception {
        mockMvc.perform(post("/test/illegal-argument"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("잘못된 요청입니다."));
    }

    @Test
    @DisplayName("일반 IllegalArgumentException은 400 대신 500 + 일반 문구다")
    void illegalArgumentWithoutKoreanMessage_returns500() throws Exception {
        mockMvc.perform(post("/test/illegal-argument-non-korean"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("서버 내부 오류가 발생했습니다."));
    }

    @Test
    @DisplayName("한글 메시지여도 BusinessValidationException이 아닌 IllegalArgumentException은 500이다")
    void plainIllegalArgumentWithKoreanMessage_returns500() throws Exception {
        mockMvc.perform(post("/test/illegal-argument-korean"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("서버 내부 오류가 발생했습니다."));
    }

    @Test
    @DisplayName("디스크 저장 실패(StorageException)는 400이 아니라 500이다")
    void storageException_returns500() throws Exception {
        mockMvc.perform(post("/test/storage-failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("서버 내부 오류가 발생했습니다."));
    }

    @Test
    @DisplayName("AccessDeniedException 메시지 끝의 내부 id는 응답에서 잘린다")
    void accessDenied_stripsTrailingInternalId() throws Exception {
        mockMvc.perform(post("/test/access-denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("작성자 본인 또는 관리자만 수정·삭제할 수 있습니다."));
    }

    @Test
    @DisplayName("검증 실패는 필드명 없는 detail과 errors[] 배열을 함께 준다")
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
    @DisplayName("유니크 위반은 409 '이미 사용 중', FK 위반은 409 '대상이 삭제·변경됨', NOT NULL은 500")
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

    @Test
    @DisplayName("If-Match 불일치는 412+EDIT_CONFLICT, 기준 버전 없음은 428+VERSION_REQUIRED로 바뀌고 댓글·글 문구가 갈린다")
    void preconditionExceptions_areMappedTo412And428() {
        ApiExceptionHandler handler = new ApiExceptionHandler();

        var post = handler.handlePreconditionFailed(
                new com.kraft.shared.domain.PreconditionFailedException(com.kraft.post.domain.Post.class, 1L));
        var comment = handler.handlePreconditionFailed(
                new com.kraft.shared.domain.PreconditionFailedException(com.kraft.comment.domain.Comment.class, 2L));
        var required = handler.handlePreconditionRequired(
                new com.kraft.shared.exception.PreconditionRequiredException("버전이 필요합니다."));

        org.assertj.core.api.Assertions.assertThat(post.getStatus()).isEqualTo(412);
        org.assertj.core.api.Assertions.assertThat(post.getProperties()).containsEntry("code", "EDIT_CONFLICT");
        org.assertj.core.api.Assertions.assertThat(post.getDetail()).contains("글");
        org.assertj.core.api.Assertions.assertThat(comment.getDetail()).contains("댓글");
        org.assertj.core.api.Assertions.assertThat(required.getStatus()).isEqualTo(428);
        org.assertj.core.api.Assertions.assertThat(required.getProperties()).containsEntry("code", "VERSION_REQUIRED");
    }
}
