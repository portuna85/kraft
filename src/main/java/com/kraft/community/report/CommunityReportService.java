package com.kraft.community.report;

import com.kraft.common.error.ApiException;
import com.kraft.community.comment.CommunityCommentRepository;
import com.kraft.community.post.CommunityPostRepository;
import com.kraft.community.user.CommunityUserRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CommunityReportService {

    private final CommunityReportRepository communityReportRepository;
    private final CommunityPostRepository communityPostRepository;
    private final CommunityCommentRepository communityCommentRepository;
    private final CommunityUserRepository communityUserRepository;
    private final Clock clock;
    private final Counter createdCounter;

    public CommunityReportService(CommunityReportRepository communityReportRepository,
                                   CommunityPostRepository communityPostRepository,
                                   CommunityCommentRepository communityCommentRepository,
                                   CommunityUserRepository communityUserRepository,
                                   Clock clock,
                                   MeterRegistry meterRegistry) {
        this.communityReportRepository = communityReportRepository;
        this.communityPostRepository = communityPostRepository;
        this.communityCommentRepository = communityCommentRepository;
        this.communityUserRepository = communityUserRepository;
        this.clock = clock;
        this.createdCounter = Counter.builder("kraft_community_report_created_total")
                .description("생성된 신고 수")
                .register(meterRegistry);
    }

    /**
     * 동일 사용자의 동일 대상 중복 신고는 REPORT_ALREADY_EXISTS 오류로 거절하며,
     * 좋아요/차단과 달리 멱등 흡수하지 않는다. 오탐으로 게시글이 부당하게 숨겨지지 않도록
     * 임계치 기반 자동 상태 전환 없이 운영 검토 대상으로만 기록한다.
     */
    public void report(Long reporterUserId, CreateReportRequest request) {
        // B-P0-5: target_id는 폴리모픽이라 FK가 없다 — 대상 종류별로 실존 여부를 직접 확인해
        // 존재하지 않는 게시글/댓글/사용자를 조용히 신고 테이블에 쌓지 않는다.
        requireTargetExists(request.targetType(), request.targetId());

        if (communityReportRepository.existsByReporterUserIdAndTargetTypeAndTargetId(
                reporterUserId, request.targetType(), request.targetId())) {
            throw new ApiException(HttpStatus.CONFLICT, "REPORT_ALREADY_EXISTS", "이미 신고한 대상입니다.");
        }
        try {
            communityReportRepository.save(new CommunityReport(
                    reporterUserId, request.targetType(), request.targetId(), request.reason(),
                    OffsetDateTime.now(clock)));
        } catch (DataIntegrityViolationException concurrentReport) {
            // B-P0-4: exists 확인과 save 사이에 동시 신고가 먼저 uk_community_reports_reporter_target을
            // 채운 경쟁 — 500 대신 신규 신고와 같은 409로 통일한다.
            throw new ApiException(HttpStatus.CONFLICT, "REPORT_ALREADY_EXISTS", "이미 신고한 대상입니다.", concurrentReport);
        }
        createdCounter.increment();
    }

    private void requireTargetExists(ReportTargetType targetType, Long targetId) {
        boolean exists = switch (targetType) {
            case POST -> communityPostRepository.existsById(targetId);
            case COMMENT -> communityCommentRepository.existsById(targetId);
            case USER -> communityUserRepository.existsById(targetId);
        };
        if (!exists) {
            throw new ApiException(HttpStatus.NOT_FOUND, "REPORT_TARGET_NOT_FOUND", "신고 대상을 찾을 수 없습니다.");
        }
    }
}
