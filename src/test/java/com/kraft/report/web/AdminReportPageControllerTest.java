package com.kraft.report.web;

import com.kraft.config.security.SecurityConfig;
import com.kraft.report.dto.ReportViewDto;
import com.kraft.report.service.ReportService;
import com.kraft.user.domain.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 관리자 신고 화면. 권한 경계와, 대상이 이미 지워진 신고도 화면이 견디는지를 본다.
 */
@WebMvcTest(AdminReportPageController.class)
@Import(SecurityConfig.class)
class AdminReportPageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportService reportService;

    @MockitoBean
    private UserRepository userRepository;

    private static Page<ReportViewDto> pageOf(ReportViewDto... rows) {
        return new PageImpl<>(List.of(rows), PageRequest.of(0, 20), rows.length);
    }

    @Test
    @DisplayName("관리자가 아니면 신고 화면에 들어갈 수 없다")
    void reports_whenNotAdmin_returns403Forbidden() throws Exception {
        mockMvc.perform(get("/admin/reports").with(user("tester@example.com").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("관리자에게는 대기 중인 신고 목록을 보여준다")
    void reports_whenAdmin_rendersPendingReports() throws Exception {
        given(reportService.findPending(any(Pageable.class))).willReturn(pageOf(new ReportViewDto(
                1L, "POST", "게시글", 10L, "광고 같은 제목", "스패머",
                "스팸·광고", "같은 글을 반복해 올립니다", "신고자", LocalDateTime.now())));

        mockMvc.perform(get("/admin/reports").with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/reports"))
                .andExpect(content().string(containsString("광고 같은 제목")))
                .andExpect(content().string(containsString("같은 글을 반복해 올립니다")));
    }

    @Test
    @DisplayName("대상이 이미 지워진 신고도 목록에서 그대로 보여준다")
    void reports_whenTargetIsGone_stillRenders() throws Exception {
        given(reportService.findPending(any(Pageable.class))).willReturn(pageOf(new ReportViewDto(
                2L, "COMMENT", "댓글", 77L, null, null,
                "욕설·비방", null, "신고자", LocalDateTime.now())));

        mockMvc.perform(get("/admin/reports").with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("대상이 이미 삭제되었습니다")));
    }

    @Test
    @DisplayName("처리할 신고가 없으면 빈 상태를 보여준다")
    void reports_whenEmpty_rendersEmptyState() throws Exception {
        given(reportService.findPending(any(Pageable.class))).willReturn(pageOf());

        mockMvc.perform(get("/admin/reports").with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("처리할 신고가 없습니다")));
    }
}
