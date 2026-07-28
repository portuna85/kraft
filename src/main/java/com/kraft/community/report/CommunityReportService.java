package com.kraft.community.report;

import com.kraft.common.error.ApiException;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CommunityReportService {

    private final CommunityReportRepository communityReportRepository;
    private final Clock clock;

    public CommunityReportService(CommunityReportRepository communityReportRepository, Clock clock) {
        this.communityReportRepository = communityReportRepository;
        this.clock = clock;
    }

    /**
     * 동일 사용자의 동일 대상 중복 신고는 오류로 거절한다(문서 13.5 REPORT_ALREADY_EXISTS —
     * 좋아요/차단과 달리 멱등 흡수 대상이 아니다). 임계치 기반 자동 상태 전환·운영 검토 큐는
     * 이번 Phase 범위 밖이다(설계 판단 4 — 오탐으로 게시글이 부당하게 숨겨지는 리스크 회피).
     */
    public void report(Long reporterUserId, CreateReportRequest request) {
        if (communityReportRepository.existsByReporterUserIdAndTargetTypeAndTargetId(
                reporterUserId, request.targetType(), request.targetId())) {
            throw new ApiException(HttpStatus.CONFLICT, "REPORT_ALREADY_EXISTS", "이미 신고한 대상입니다.");
        }
        communityReportRepository.save(new CommunityReport(
                reporterUserId, request.targetType(), request.targetId(), request.reason(),
                OffsetDateTime.now(clock)));
    }
}
