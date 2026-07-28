package com.kraft.community.report;

import com.kraft.common.error.ApiException;
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
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("커뮤니티 신고 서비스 단위 테스트")
class CommunityReportServiceTest {

    @Mock
    private CommunityReportRepository communityReportRepository;

    private CommunityReportService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-28T00:00:00Z"), ZoneOffset.UTC);
        service = new CommunityReportService(communityReportRepository, clock, new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("처음 신고하면 저장된다")
    void report_firstTime_saves() {
        given(communityReportRepository.existsByReporterUserIdAndTargetTypeAndTargetId(
                1L, ReportTargetType.POST, 10L)).willReturn(false);

        service.report(1L, new CreateReportRequest(ReportTargetType.POST, 10L, ReportReason.SPAM));

        verify(communityReportRepository).save(any());
    }

    @Test
    @DisplayName("동일 대상 중복 신고는 409 REPORT_ALREADY_EXISTS로 거부된다")
    void report_duplicate_throwsConflict() {
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
}
