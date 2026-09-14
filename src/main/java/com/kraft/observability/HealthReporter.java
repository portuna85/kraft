package com.kraft.observability;

import com.kraft.user.mail.OutboxMailRepository;
import com.kraft.user.mail.OutboxMailStatus;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.List;

/**
 * 주기적으로 앱 상태를 훑어 {@code logs/kraft-metrics.log}에 한 줄 남기고, 기준을 넘긴 것이
 * 있으면 ERROR로 올려 {@code logs/kraft-error.log}에도 남긴다.
 *
 * <h3>왜 Actuator가 아닌가</h3>
 * Actuator는 커밋 {@code 1080614}에서 "앱을 호스트에서 직접 실행하게 되면서 상태 확인
 * 엔드포인트를 쓰는 곳이 없어졌다"는 이유로 제거됐다. 지금도 헬스 프로브를 찌를 오케스트레이터가
 * 없으므로 되돌리면 같은 문제가 반복된다. 대신 <b>앱이 스스로 보고</b> 기존 로그 체계에 남긴다.
 * 나중에 로그 수집기를 붙이면 이 줄들이 그대로 지표와 알림이 되므로 버려지는 작업이 아니다.
 *
 * <h3>수집하는 것</h3>
 * HTTP 오류율·응답 지연({@link RequestMetrics}), DB 커넥션 풀, 업로드 디스크 여유,
 * 메일 대기열. 전부 "이게 막히면 사용자가 곧바로 겪는" 것들이다.
 */
@Slf4j
public class HealthReporter {

    private final RequestMetrics requestMetrics;
    private final OutboxMailRepository outboxMailRepository;
    private final DataSource dataSource;
    private final Path uploadDir;

    @Value("${app.metrics.enabled:true}")
    private boolean enabled;

    @Value("${app.metrics.min-requests:20}")
    private int minRequests;

    @Value("${app.metrics.error-rate:0.1}")
    private double errorRate;

    @Value("${app.metrics.server-errors:0}")
    private long serverErrors;

    @Value("${app.metrics.avg-response-ms:1000}")
    private long avgMillis;

    @Value("${app.metrics.pool-usage:0.8}")
    private double poolUsage;

    @Value("${app.metrics.disk-free-bytes:1073741824}")
    private long diskFreeBytes;

    @Value("${app.metrics.mail-pending:20}")
    private long mailPending;

    @Value("${app.metrics.mail-failed:0}")
    private long mailFailed;

    public HealthReporter(RequestMetrics requestMetrics,
                          OutboxMailRepository outboxMailRepository,
                          DataSource dataSource,
                          String uploadDir) {
        this.requestMetrics = requestMetrics;
        this.outboxMailRepository = outboxMailRepository;
        this.dataSource = dataSource;
        this.uploadDir = Path.of(uploadDir).toAbsolutePath();
    }

    @Scheduled(initialDelayString = "${app.metrics.initial-delay-ms:60000}",
            fixedDelayString = "${app.metrics.interval-ms:300000}")
    public void report() {
        if (!enabled) {
            return;
        }
        try {
            report(collect());
        } catch (Exception e) {
            // 관측이 실패해도 서비스는 계속되어야 한다. 다만 조용히 멈추면 "지표가 없는데
            // 아무도 모르는" 상태가 되므로 실패 자체를 남긴다.
            log.error("상태 점검에 실패했습니다.", e);
        }
    }

    /** 수집한 뒤 판정해 기록한다. 테스트가 임의의 스냅숏으로 이 경로만 확인할 수 있게 분리했다. */
    void report(HealthSnapshot snapshot) {
        List<String> breaches = snapshot.breaches(thresholds());
        if (breaches.isEmpty()) {
            log.info("상태 정상 | {}", snapshot.summary());
        } else {
            log.error("상태 이상: {} | {}", String.join(", ", breaches), snapshot.summary());
        }
    }

    HealthThresholds thresholds() {
        return new HealthThresholds(minRequests, errorRate, serverErrors, avgMillis,
                poolUsage, diskFreeBytes, mailPending, mailFailed);
    }

    HealthSnapshot collect() {
        RequestMetrics.Snapshot http = requestMetrics.drain();
        HikariPoolMXBean pool = pool();

        return new HealthSnapshot(
                http.requests(), http.errors(), http.serverErrors(), http.avgMillis(), http.maxMillis(),
                pool == null ? 0 : pool.getActiveConnections(),
                pool == null ? 0 : poolSize(),
                pool == null ? 0 : pool.getThreadsAwaitingConnection(),
                usableSpace(),
                outboxMailRepository.countByStatus(OutboxMailStatus.PENDING),
                outboxMailRepository.countByStatus(OutboxMailStatus.FAILED));
    }

    private HikariPoolMXBean pool() {
        // 테스트(H2)든 운영(MariaDB)이든 Hikari지만, 다른 풀로 바뀌어도 관측이 앱을 막으면 안 된다.
        return dataSource instanceof HikariDataSource hikari ? hikari.getHikariPoolMXBean() : null;
    }

    private int poolSize() {
        return ((HikariDataSource) dataSource).getMaximumPoolSize();
    }

    /**
     * 업로드 디렉터리가 있는 파일시스템의 여유 공간. 디렉터리가 아직 없으면 존재하는 상위로
     * 거슬러 올라간다 — 첫 업로드 전에는 만들어지지 않기 때문이다.
     */
    private long usableSpace() {
        for (Path path = uploadDir; path != null; path = path.getParent()) {
            if (path.toFile().exists()) {
                return path.toFile().getUsableSpace();
            }
        }
        return 0;
    }
}
