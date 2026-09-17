package com.kraft.report.web;

import com.kraft.config.security.SecurityConfig;
import com.kraft.report.domain.ReportReason;
import com.kraft.report.domain.ReportTargetType;
import com.kraft.report.dto.ReportSaveRequestDto;
import com.kraft.report.service.ReportService;
import com.kraft.user.domain.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 신고 API의 경계 검증. 여기서 가장 중요한 것은 <b>처리 엔드포인트가 관리자 전용인가</b>이다 —
 * 화면에서 버튼을 감추는 것만으로는 아무것도 막지 못한다.
 */
@WebMvcTest(ReportApiController.class)
@Import(SecurityConfig.class)
class ReportApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportService reportService;

    /** SecurityConfig가 UserDetailsService를 필요로 한다(폼 로그인 구성). */
    @MockitoBean
    private UserRepository userRepository;

    @Test
    @DisplayName("POST /api/v1/reports 는 로그인+CSRF면 접수하고 id를 반환한다")
    void report_whenAuthenticated_returnsId() throws Exception {
        given(reportService.report(any(ReportSaveRequestDto.class), any(Authentication.class))).willReturn(3L);

        mockMvc.perform(post("/api/v1/reports")
                        .with(user("reporter@example.com").roles("GUEST"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"POST\",\"targetId\":10,\"reason\":\"SPAM\",\"detail\":\"광고입니다\"}"))
                .andExpect(status().isOk());

        verify(reportService).report(
                eq(new ReportSaveRequestDto(ReportTargetType.POST, 10L, ReportReason.SPAM, "광고입니다")),
                any(Authentication.class));
    }

    @Test
    @DisplayName("POST /api/v1/reports 는 미인증이면 로그인 페이지로 보내고 서비스는 호출되지 않는다")
    void report_whenNotAuthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(post("/api/v1/reports")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"POST\",\"targetId\":10,\"reason\":\"SPAM\"}"))
                .andExpect(status().is3xxRedirection());

        verifyNoInteractions(reportService);
    }

    @Test
    @DisplayName("POST /api/v1/reports 는 사유가 없으면 400이고 서비스는 호출되지 않는다")
    void report_withoutReason_returns400BadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/reports")
                        .with(user("reporter@example.com").roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"POST\",\"targetId\":10}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(reportService);
    }

    @Test
    @DisplayName("POST /api/v1/reports 는 이미 신고한 대상이면 400 ProblemDetail을 반환한다")
    void report_whenAlreadyReported_returns400BadRequest() throws Exception {
        willThrow(new IllegalArgumentException("이미 신고한 대상입니다. 관리자가 확인하고 있습니다."))
                .given(reportService).report(any(ReportSaveRequestDto.class), any(Authentication.class));

        mockMvc.perform(post("/api/v1/reports")
                        .with(user("reporter@example.com").roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"POST\",\"targetId\":10,\"reason\":\"SPAM\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("이미 신고한 대상입니다. 관리자가 확인하고 있습니다."));
    }

    @Test
    @DisplayName("신고 처리는 관리자만 할 수 있다 — 일반 회원은 403이고 서비스는 호출되지 않는다")
    void resolve_whenNotAdmin_returns403Forbidden() throws Exception {
        mockMvc.perform(post("/api/v1/admin/reports/5/resolve")
                        .with(user("tester@example.com").roles("USER"))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(reportService);
    }

    @Test
    @DisplayName("관리자는 신고를 처리(대상 삭제)할 수 있다")
    void resolve_whenAdmin_returns204NoContent() throws Exception {
        mockMvc.perform(post("/api/v1/admin/reports/5/resolve")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(reportService).resolve(eq(5L), any(Authentication.class), eq(0));
    }

    @Test
    @DisplayName("관리자는 처리하면서 작성자를 정지할 수 있다 — 지우기만 해서는 반복을 못 막는다")
    void resolveWithSuspendDays_passesTheDurationThrough() throws Exception {
        mockMvc.perform(post("/api/v1/admin/reports/5/resolve")
                        .param("suspendDays", "7")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(reportService).resolve(eq(5L), any(Authentication.class), eq(7));
    }

    @Test
    @DisplayName("B11: 다른 관리자가 먼저 처리해 버전이 충돌하면 409로 안내한다")
    void resolve_whenOptimisticLockConflicts_returns409Conflict() throws Exception {
        willThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(
                com.kraft.report.domain.Report.class, 5L))
                .given(reportService).resolve(eq(5L), any(Authentication.class), eq(0));

        mockMvc.perform(post("/api/v1/admin/reports/5/resolve")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("이미 처리된 신고입니다. 새로고침 후 다시 확인해 주세요."));
    }

    @Test
    @DisplayName("관리자는 신고를 반려할 수 있다")
    void reject_whenAdmin_returns204NoContent() throws Exception {
        mockMvc.perform(post("/api/v1/admin/reports/5/reject")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(reportService).reject(eq(5L), any(Authentication.class));
    }
}
