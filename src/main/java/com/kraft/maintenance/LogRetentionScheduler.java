package com.kraft.maintenance;

import com.kraft.admin.AdminAuditLogRepository;
import com.kraft.operationlog.WinningNumberOperationLogRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;

// KB-17: operationlog·admin 양쪽 로그 테이블을 한 스케줄에서 함께 정리하는 의도적 설계다
// (교차 의존 자체는 없앨 수 없다). 예전에는 operationlog 패키지 안에 있어서 그 패키지가
// admin에 의존하는 것처럼 보였는데(operationlog→admin, 순환의 한 변), 이 클래스가
// 정리(maintenance) 역할이지 operationlog 도메인 로직이 아니라는 사실을 패키지로도
// 드러내기 위해 별도 leaf 패키지로 옮겼다 — 아무도 maintenance에 의존하지 않는다.
@Component
public class LogRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(LogRetentionScheduler.class);

    private final WinningNumberOperationLogRepository operationLogRepository;
    private final AdminAuditLogRepository adminAuditLogRepository;
    private final Clock clock;

    @Value("${kraft.retention.operation-log-days:30}")
    private int operationLogRetentionDays;

    @Value("${kraft.retention.admin-audit-log-days:90}")
    private int adminAuditLogRetentionDays;

    public LogRetentionScheduler(WinningNumberOperationLogRepository operationLogRepository,
                                 AdminAuditLogRepository adminAuditLogRepository,
                                 Clock clock) {
        this.operationLogRepository = operationLogRepository;
        this.adminAuditLogRepository = adminAuditLogRepository;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "purge-old-logs", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    @Transactional
    public void purgeOldLogs() {
        OffsetDateTime operationLogCutoff = OffsetDateTime.now(clock).minusDays(operationLogRetentionDays);
        int deletedOperationLogs = operationLogRepository.deleteByCreatedAtBefore(operationLogCutoff);
        log.info("작업 로그 보관기간 초과 행 삭제 완료: cutoff={} deleted={}", operationLogCutoff, deletedOperationLogs);

        OffsetDateTime adminAuditLogCutoff = OffsetDateTime.now(clock).minusDays(adminAuditLogRetentionDays);
        int deletedAdminAuditLogs = adminAuditLogRepository.deleteByCreatedAtBefore(adminAuditLogCutoff);
        log.info("관리자 감사 로그 보관기간 초과 행 삭제 완료: cutoff={} deleted={}", adminAuditLogCutoff, deletedAdminAuditLogs);
    }
}
