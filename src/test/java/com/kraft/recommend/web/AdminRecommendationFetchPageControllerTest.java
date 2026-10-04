package com.kraft.recommend.web;

import com.kraft.config.security.SecurityConfig;
import com.kraft.recommend.domain.DrawDetails;
import com.kraft.recommend.domain.RecommendationFetchAttempt;
import com.kraft.recommend.domain.RecommendationFetchAttemptRepository;
import com.kraft.recommend.domain.RecommendationHistoryState;
import com.kraft.recommend.domain.RecommendationHistoryStateRepository;
import com.kraft.recommend.domain.WinningDraw;
import com.kraft.recommend.domain.WinningDrawRepository;
import com.kraft.recommend.service.RecommendationFetchStatus;
import com.kraft.user.domain.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/** 자동 수집 상태 화면의 권한 경계와 표시 내용. */
@WebMvcTest(AdminRecommendationFetchPageController.class)
@Import(SecurityConfig.class)
class AdminRecommendationFetchPageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecommendationHistoryStateRepository stateRepository;

    @MockitoBean
    private RecommendationFetchStatus fetchStatus;

    @MockitoBean
    private WinningDrawRepository winningDrawRepository;

    @MockitoBean
    private RecommendationFetchAttemptRepository attemptRepository;

    /** SecurityConfig가 UserDetailsService를 필요로 한다(폼 로그인 구성). */
    @MockitoBean
    private UserRepository userRepository;

    @Test
    @DisplayName("관리자가 아니면 수집 상태 화면에 들어갈 수 없다")
    void fetchStatus_whenNotAdmin_returns403Forbidden() throws Exception {
        mockMvc.perform(get("/admin/recommendations").with(user("tester@example.com").roles("USER")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(stateRepository, fetchStatus, winningDrawRepository, attemptRepository);
    }

    @Test
    @DisplayName("관리자에게는 검증 회차·다음 대상·연속 실패와 사유를 보여준다")
    void fetchStatus_whenAdmin_rendersState() throws Exception {
        given(stateRepository.findById(1)).willReturn(Optional.of(RecommendationHistoryState.builder()
                .id(1).version(3L).verifiedThroughRound(1200).sourceReference("dhlottery-api-auto")
                .verifiedAt(LocalDateTime.of(2026, 10, 3, 21, 31)).build()));
        given(fetchStatus.consecutiveFailures()).willReturn(2);
        given(fetchStatus.lastFailureAt()).willReturn(Instant.parse("2026-10-03T12:35:00Z"));
        given(fetchStatus.lastFailureReason()).willReturn("봇 차단");

        mockMvc.perform(get("/admin/recommendations").with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/recommendations"))
                .andExpect(content().string(containsString("1200회")))
                .andExpect(content().string(containsString("1201회")))
                .andExpect(content().string(containsString("2회")))
                .andExpect(content().string(containsString("2026.10.03 21:35")))
                .andExpect(content().string(containsString("봇 차단")));
    }

    @Test
    @DisplayName("다음 예약 시각·DB의 최신 회차·최근 수집 이력과 '지금 수집' 버튼을 보여준다")
    void fetchStatus_whenAdmin_rendersNextRunLatestDrawAndHistory() throws Exception {
        WinningDraw latest = WinningDraw.builder().roundNo(1244).numbers(List.of(1, 13, 18, 26, 34, 38))
                .updatedAt(LocalDateTime.now()).build();
        latest.applyDetails(new DrawDetails(25, LocalDate.of(2026, 10, 3), null, null));
        given(winningDrawRepository.findTopByOrderByRoundNoDesc()).willReturn(Optional.of(latest));
        given(attemptRepository.findAllByOrderByIdDesc(any())).willReturn(List.of(
                RecommendationFetchAttempt.builder().attemptedAt(LocalDateTime.of(2026, 10, 4, 10, 51))
                        .trigger(RecommendationFetchAttempt.Trigger.MANUAL)
                        .outcome(RecommendationFetchAttempt.Outcome.FAILED).roundNo(1244).detail("HTTP_ERROR: 500").build(),
                RecommendationFetchAttempt.builder().attemptedAt(LocalDateTime.of(2026, 10, 3, 21, 31))
                        .trigger(RecommendationFetchAttempt.Trigger.SCHEDULED)
                        .outcome(RecommendationFetchAttempt.Outcome.FETCHED).roundNo(1243).build()));

        mockMvc.perform(get("/admin/recommendations").with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("다음 예약 수집")))
                .andExpect(content().string(containsString("1244회")))
                .andExpect(content().string(containsString("2026.10.03")))
                .andExpect(content().string(containsString("최근 수집 이력")))
                .andExpect(content().string(containsString("HTTP_ERROR: 500")))
                .andExpect(content().string(containsString("수동")))
                .andExpect(content().string(containsString("btn-fetch-now")));
    }

    @Test
    @DisplayName("상태 행이 없고 기록이 비어 있어도 화면이 뜬다")
    void fetchStatus_whenNothingRecorded_rendersPlaceholders() throws Exception {
        given(stateRepository.findById(1)).willReturn(Optional.empty());

        mockMvc.perform(get("/admin/recommendations").with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("아직 없음")))
                .andExpect(content().string(containsString("기록 없음")));
    }
}
