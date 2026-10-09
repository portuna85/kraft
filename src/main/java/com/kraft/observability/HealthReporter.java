package com.kraft.observability;

import com.kraft.post.domain.PostImageRepository;
import com.kraft.post.domain.PostImageStatus;
import com.kraft.recommend.domain.RecommendationHistoryState;
import com.kraft.recommend.domain.RecommendationHistoryStateRepository;
import com.kraft.recommend.service.RecommendationFetchStatus;
import com.kraft.user.mail.OutboxMailRepository;
import com.kraft.user.mail.OutboxMailStatus;
import com.kraft.user.session.SessionRevocationTaskRepository;
import com.kraft.user.session.SessionRevocationTaskStatus;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 주기적으로 앱 상태를 훑어 {@code logs/kraft-metrics.log}에 한 줄 남기고, 기준을 넘기면 ERROR로 올려
 * {@code logs/kraft-error.log}에도 남긴다. Actuator 대신 앱이 스스로 보고한다(상태 프로브를 찌를
 * 오케스트레이터가 없다). 수집 항목은 HTTP 오류율·지연({@link RequestMetrics}), DB 풀, 디스크 여유, 메일 대기열,
 * 그리고 주기 작업이 막혀도 기존 지표에 드러나지 않는 세션 폐기 실패·이미지 삭제 backlog·추천 이력 최신성이다.
 */
@Slf4j
public class HealthReporter {

    private final RequestMetrics requestMetrics;
    private final OutboxMailRepository outboxMailRepository;
    private final SessionRevocationTaskRepository sessionRevocationTaskRepository;
    private final PostImageRepository postImageRepository;
    private final RecommendationHistoryStateRepository recommendationHistoryStateRepository;
    private final DataSource dataSource;
    private final Path uploadDir;
    private final AlertMailer alertMailer;
    private final MetricsProperties properties;
    /** {@code app.recommend.enabled}. 이력 나이의 -1을 "기능 꺼짐"과 "미준비"로 구분한다. */
    private final boolean recommendEnabled;
    /** 자동 수집의 연속 실패 수(없으면 측정하지 않는다). */
    private final RecommendationFetchStatus recommendationFetchStatus;

    public HealthReporter(RequestMetrics requestMetrics,
                          OutboxMailRepository outboxMailRepository,
                          SessionRevocationTaskRepository sessionRevocationTaskRepository,
                          PostImageRepository postImageRepository,
                          RecommendationHistoryStateRepository recommendationHistoryStateRepository,
                          DataSource dataSource,
                          String uploadDir,
                          AlertMailer alertMailer,
                          MetricsProperties properties,
                          boolean recommendEnabled,
                          RecommendationFetchStatus recommendationFetchStatus) {
        this.requestMetrics = requestMetrics;
        this.outboxMailRepository = outboxMailRepository;
        this.sessionRevocationTaskRepository = sessionRevocationTaskRepository;
        this.postImageRepository = postImageRepository;
        this.recommendationHistoryStateRepository = recommendationHistoryStateRepository;
        this.dataSource = dataSource;
        this.uploadDir = Path.of(uploadDir).toAbsolutePath();
        this.alertMailer = alertMailer;
        this.properties = properties;
        this.recommendEnabled = recommendEnabled;
        this.recommendationFetchStatus = recommendationFetchStatus;
    }

    @Scheduled(initialDelayString = "${app.metrics.initial-delay-ms:60000}",
            fixedDelayString = "${app.metrics.interval-ms:300000}")
    public void report() {
        if (!properties.enabled()) {
            return;
        }
        try {
            report(collect());
        } catch (Exception e) {
            // 관측이 실패해도 서비스는 계속되되, 조용히 멈추지 않게 실패를 남긴다.
            log.error("상태 점검에 실패했습니다.", e);
        }
    }

    /** 수집한 뒤 판정해 기록한다. 테스트가 임의의 스냅숏으로 이 경로만 확인할 수 있게 분리했다. */
    void report(HealthSnapshot snapshot) {
        HealthThresholds thresholds = thresholds();
        List<String> breaches = snapshot.breaches(thresholds);
        if (breaches.isEmpty()) {
            log.info("상태 정상 | {}", snapshot.summary());
        } else {
            log.error("상태 이상: {} | {}", String.join(", ", breaches), snapshot.summary());
            alertMailer.alertIfDue(snapshot, thresholds);
        }
    }

    HealthThresholds thresholds() {
        return properties.thresholds();
    }

    HealthSnapshot collect() {
        // drain()이 다음 주기를 위해 RequestMetrics를 비우므로, 이후 DB 집계가 실패해도 이 HTTP 스냅숏은
        // 잃지 않게 집계마다 개별로 감싸 실패한 필드만 -1(측정 불가)로 표시한다.
        RequestMetrics.Snapshot http = requestMetrics.drain();
        HikariPoolMXBean pool = pool();

        return new HealthSnapshot(
                http.requests(), http.errors(), http.serverErrors(), http.avgMillis(), http.maxMillis(),
                pool == null ? 0 : pool.getActiveConnections(),
                pool == null ? 0 : poolSize(),
                pool == null ? 0 : pool.getThreadsAwaitingConnection(),
                usableSpace(),
                safeCount("발송 대기 메일 수", () -> outboxMailRepository.countByStatus(OutboxMailStatus.PENDING)),
                safeCount("발송 포기 메일 수", () -> outboxMailRepository.countByStatus(OutboxMailStatus.FAILED)),
                http.slowRequests(),
                safeCount("세션 폐기 실패 수", () -> sessionRevocationTaskRepository.countByStatus(SessionRevocationTaskStatus.FAILED)),
                safeCount("이미지 삭제 backlog", () -> postImageRepository.countByStatus(PostImageStatus.PENDING_DELETE)),
                recommendationHistoryAgeHours(),
                recommendEnabled,
                recommendationFetchStatus == null ? 0 : recommendationFetchStatus.consecutiveFailures());
    }

    /** 추천 이력 검증 기준의 경과 시간. 상태 행이 없거나 검증된 적이 없으면 -1(측정 불가). */
    private long recommendationHistoryAgeHours() {
        return safeCount("추천 이력 검증 기준 시각", () -> {
            RecommendationHistoryState state = recommendationHistoryStateRepository.findById(1).orElse(null);
            LocalDateTime verifiedAt = state == null ? null : state.getVerifiedAt();
            if (verifiedAt == null) {
                return -1L;
            }
            return Duration.between(verifiedAt, LocalDateTime.now()).toHours();
        });
    }

    /** 조회에 실패하면 경고를 남기고 -1(측정 불가)을 돌려준다. */
    private long safeCount(String what, java.util.function.LongSupplier query) {
        try {
            return query.getAsLong();
        } catch (Exception e) {
            log.warn("{} 집계에 실패했습니다 — 이번 주기는 측정 불가(-1)로 남긴다.", what, e);
            return -1;
        }
    }

    private HikariPoolMXBean pool() {
        // 다른 풀로 바뀌어도 관측이 앱을 막으면 안 된다.
        return dataSource instanceof HikariDataSource hikari ? hikari.getHikariPoolMXBean() : null;
    }

    private int poolSize() {
        return ((HikariDataSource) dataSource).getMaximumPoolSize();
    }

    /** 업로드 디렉터리가 있는 파일시스템의 여유 공간(디렉터리가 아직 없으면 존재하는 상위로 거슬러 간다). 측정 불가면 -1. */
    private long usableSpace() {
        for (Path path = uploadDir; path != null; path = path.getParent()) {
            if (path.toFile().exists()) {
                return path.toFile().getUsableSpace();
            }
        }
        return -1;
    }
}
