package com.kraft.community.report;

import com.kraft.common.error.ApiException;
import com.kraft.community.comment.CommunityCommentRepository;
import com.kraft.community.post.CommunityPostRepository;
import com.kraft.community.user.CommunityUserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("커뮤니티 신고 서비스 단위 테스트")
class CommunityReportServiceTest {

    @Mock
    private CommunityReportRepository communityReportRepository;

    @Mock
    private CommunityPostRepository communityPostRepository;

    @Mock
    private CommunityCommentRepository communityCommentRepository;

    @Mock
    private CommunityUserRepository communityUserRepository;

    private CommunityReportService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-28T00:00:00Z"), ZoneOffset.UTC);
        service = new CommunityReportService(communityReportRepository, communityPostRepository,
                communityCommentRepository, communityUserRepository, clock, new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("처음 신고하면 저장된다")
    void report_firstTime_saves() {
        given(communityPostRepository.existsById(10L)).willReturn(true);
        given(communityReportRepository.existsByReporterUserIdAndTargetTypeAndTargetId(
                1L, ReportTargetType.POST, 10L)).willReturn(false);

        service.report(1L, new CreateReportRequest(ReportTargetType.POST, 10L, ReportReason.SPAM));

        verify(communityReportRepository).save(any());
    }

    @Test
    @DisplayName("동일 대상 중복 신고는 409 REPORT_ALREADY_EXISTS로 거부된다")
    void report_duplicate_throwsConflict() {
        given(communityPostRepository.existsById(10L)).willReturn(true);
        given(communityReportRepository.existsByReporterUserIdAndTargetTypeAndTargetId(
                1L, ReportTargetType.POST, 10L)).willReturn(true);

        assertThatThrownBy(() -> service.report(1L, new CreateReportRequest(ReportTargetType.POST, 10L, ReportReason.SPAM)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(apiEx.getCode()).isEqualTo("REPORT_ALREADY_EXISTS");
                });
        verify(communityReportRepository, never()).save(any());
    }

    @Test
    @DisplayName("B-P0-5: 존재하지 않는 게시글은 신고할 수 없다")
    void report_postTargetNotFound_throwsNotFound() {
        given(communityPostRepository.existsById(10L)).willReturn(false);

        assertThatThrownBy(() -> service.report(1L, new CreateReportRequest(ReportTargetType.POST, 10L, ReportReason.SPAM)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(apiEx.getCode()).isEqualTo("REPORT_TARGET_NOT_FOUND");
                });
        verify(communityReportRepository, never()).existsByReporterUserIdAndTargetTypeAndTargetId(any(), any(), any());
    }

    @Test
    @DisplayName("B-P0-5: 존재하지 않는 댓글은 신고할 수 없다")
    void report_commentTargetNotFound_throwsNotFound() {
        given(communityCommentRepository.existsById(20L)).willReturn(false);

        assertThatThrownBy(() -> service.report(1L, new CreateReportRequest(ReportTargetType.COMMENT, 20L, ReportReason.SPAM)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("REPORT_TARGET_NOT_FOUND"));
    }

    @Test
    @DisplayName("B-P0-5: 존재하지 않는 사용자는 신고할 수 없다")
    void report_userTargetNotFound_throwsNotFound() {
        given(communityUserRepository.existsById(30L)).willReturn(false);

        assertThatThrownBy(() -> service.report(1L, new CreateReportRequest(ReportTargetType.USER, 30L, ReportReason.SPAM)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("REPORT_TARGET_NOT_FOUND"));
    }

    @Test
    @DisplayName("B-P0-4: exists 확인과 저장 사이 경쟁이 나면 500 대신 409 REPORT_ALREADY_EXISTS로 통일한다")
    void report_raceLostAfterCheck_throwsConflict() {
        given(communityPostRepository.existsById(10L)).willReturn(true);
        given(communityReportRepository.existsByReporterUserIdAndTargetTypeAndTargetId(
                1L, ReportTargetType.POST, 10L)).willReturn(false);
        willThrow(new DataIntegrityViolationException("uk_community_reports_reporter_target"))
                .given(communityReportRepository).save(any());

        assertThatThrownBy(() -> service.report(1L, new CreateReportRequest(ReportTargetType.POST, 10L, ReportReason.SPAM)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(apiEx.getCode()).isEqualTo("REPORT_ALREADY_EXISTS");
                });
    }
}
