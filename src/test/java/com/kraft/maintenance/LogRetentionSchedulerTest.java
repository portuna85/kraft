package com.kraft.maintenance;

import com.kraft.admin.AdminAuditLog;
import com.kraft.admin.AdminAuditLogRepository;
import com.kraft.operationlog.WinningNumberOperationLog;
import com.kraft.operationlog.WinningNumberOperationLogRepository;
import com.kraft.operationlog.WinningNumberOperationStatus;
import com.kraft.winningnumber.WinningNumberOperationType;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("로그 보존 스케줄러 테스트")
class LogRetentionSchedulerTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Instant NOW = Instant.parse("2026-07-17T00:00:00Z");

    @Autowired
    private LogRetentionScheduler scheduler;

    @Autowired
    private WinningNumberOperationLogRepository operationLogRepository;

    @Autowired
    private AdminAuditLogRepository adminAuditLogRepository;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void setUp() {
        operationLogRepository.deleteAll();
        adminAuditLogRepository.deleteAll();
        given(clock.instant()).willReturn(NOW);
        given(clock.getZone()).willReturn(KST);
    }

    @Test
    @DisplayName("보관기간을 초과한 로그만 벌크 삭제되고 이내인 로그는 남는다")
    void purgeOldLogs_deletesOnlyLogsOlderThanRetention() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        // 작업 로그 보존기간 기본 30일 — 31일 전은 삭제, 1일 전은 유지
        operationLogRepository.save(new WinningNumberOperationLog(
                WinningNumberOperationType.EXTERNAL_COLLECT, WinningNumberOperationStatus.SUCCESS,
                1200, null, null, null, now.minusDays(31)));
        operationLogRepository.save(new WinningNumberOperationLog(
                WinningNumberOperationType.EXTERNAL_COLLECT, WinningNumberOperationStatus.SUCCESS,
                1201, null, null, null, now.minusDays(1)));

        // 관리자 감사 로그 보존기간 기본 90일 — 91일 전은 삭제, 1일 전은 유지
        adminAuditLogRepository.save(new AdminAuditLog(
                "admin", "LOGIN_SUCCESS", null, null, "127.0.0.1", now.minusDays(91)));
        adminAuditLogRepository.save(new AdminAuditLog(
                "admin", "LOGIN_SUCCESS", null, null, "127.0.0.1", now.minusDays(1)));

        scheduler.purgeOldLogs();

        assertThat(operationLogRepository.findAll())
                .hasSize(1)
                .allMatch(l -> l.getRound() == 1201);
        assertThat(adminAuditLogRepository.findAll()).hasSize(1);
    }
}
