package com.kraft.community.report;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunityReportRepository extends JpaRepository<CommunityReport, Long> {

    boolean existsByReporterUserIdAndTargetTypeAndTargetId(Long reporterUserId, ReportTargetType targetType,
                                                            Long targetId);
}
