package com.kraft.observability;

import com.kraft.post.domain.PostImageRepository;
import com.kraft.post.domain.PostImageStatus;
import com.kraft.recommend.domain.RecommendationHistoryState;
import com.kraft.recommend.domain.RecommendationHistoryStateRepository;
import com.kraft.report.domain.ReportRepository;
import com.kraft.report.domain.ReportStatus;
import com.kraft.user.mail.OutboxMailRepository;
import com.kraft.user.mail.OutboxMailStatus;
import com.kraft.user.session.SessionRevocationTaskRepository;
import com.kraft.user.session.SessionRevocationTaskStatus;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
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
 * 메일 대기열, 미처리 신고. 앞의 것들은 "막히면 사용자가 곧바로 겪는" 것이고, 마지막 하나는
 * 앱이 아니라 사람이 멈춘 신호다 — 신고가 쌓이는 동안 문제가 된 글은 그대로 보인다.
 * <p>
 * 세션 폐기 실패 건수·이미지 삭제 backlog·추천 이력 최신성도 같은 이유로 담는다(O03) —
 * 셋 다 주기 작업이 있지만 그 작업 자체가 막히거나 계속 실패해도 기존 지표에는 드러나지
 * 않았다.
 */
@Slf4j
public class HealthReporter {

    private final RequestMetrics requestMetrics;
    private final OutboxMailRepository outboxMailRepository;
    private final ReportRepository reportRepository;
    private final SessionRevocationTaskRepository sessionRevocationTaskRepository;
    private final PostImageRepository postImageRepository;
    private final RecommendationHistoryStateRepository recommendationHistoryStateRepository;
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

    /** 신고는 사람이 처리한다. 하루치가 쌓이도록 아무도 보지 않았다면 그것이 알릴 일이다. */
    @Value("${app.metrics.reports-pending:20}")
    private long reportsPending;

    /** 평균이 정상이어도 이만큼 넘게 느린 요청이 있으면 알린다(O05). */
    @Value("${app.metrics.slow-requests:5}")
    private long slowRequests;

    /** 재시도를 모두 소진해 사람이 봐야 하는 세션 폐기 태스크 수 상한(O03). */
    @Value("${app.metrics.session-revocation-failed:0}")
    private long sessionRevocationFailed;

    /** 삭제 예약됐지만 아직 실제로 지우지 못한 이미지 파일 수 상한(O03). */
    @Value("${app.metrics.image-delete-backlog:200}")
    private long imageDeleteBacklog;

    /**
     * 추천 이력 검증 기준(verifiedAt)이 이만큼(시간) 지나면 알린다(O03). 매주 토요일 밤
     * 자동 수집이 실패하거나 꺼져 있으면 이 값이 계속 커진다. 기본값(200시간 ≈ 8.3일)은
     * 주 1회 수집이 한 번 밀려도 곧바로 울리지 않을 만큼의 여유다 — 실측 후 조정 대상이다.
     */
    @Value("${app.metrics.recommendation-history-stale-hours:200}")
    private long recommendationHistoryStaleHours;

    /**
     * recommendationHistoryAgeHours의 -1을 "측정 실패"와 "기능이 꺼져 있어 이력이 애초에
     * 없음"으로 구분하는 데 쓴다(개선 보고서 OBS-01). {@code app.recommend.enabled}와 같은
     * 프로퍼티를 읽는다 — {@code RecommendationApiController}·{@code RecommendationPageController}가
     * 이 값으로 빈 등록 여부를 결정하는 것과 같다.
     */
    @Value("${app.recommend.enabled:true}")
    private boolean recommendEnabled;

    public HealthReporter(RequestMetrics requestMetrics,
                          OutboxMailRepository outboxMailRepository,
                          ReportRepository reportRepository,
                          SessionRevocationTaskRepository sessionRevocationTaskRepository,
                          PostImageRepository postImageRepository,
                          RecommendationHistoryStateRepository recommendationHistoryStateRepository,
                          DataSource dataSource,
                          String uploadDir) {
        this.requestMetrics = requestMetrics;
        this.outboxMailRepository = outboxMailRepository;
        this.reportRepository = reportRepository;
        this.sessionRevocationTaskRepository = sessionRevocationTaskRepository;
        this.postImageRepository = postImageRepository;
        this.recommendationHistoryStateRepository = recommendationHistoryStateRepository;
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
                poolUsage, diskFreeBytes, mailPending, mailFailed, reportsPending, slowRequests,
                sessionRevocationFailed, imageDeleteBacklog, recommendationHistoryStaleHours);
    }

    HealthSnapshot collect() {
        // drain()은 부르는 순간 다음 주기를 위해 RequestMetrics를 비운다 — 이 시점 이후로
        // 무엇이 실패하든 이 HTTP 스냅숏은 이미 확보되어 있어야 한다. 예전에는 DB 집계
        // 조회를 이 메서드 안에서 예외 없이 그대로 불러, 그 조회 하나가 실패하면(DB 순단 등)
        // collect() 전체가 예외로 끝나 report()의 catch까지 올라가고, 이미 드레인해 비워
        // 버린 HTTP 스냅숏은 어디에도 기록되지 못한 채 통째로 사라졌다(개선 보고서 "관측
        // 수집 실패 시 스냅숏 유실"). DB 집계 각각을 개별로 감싸, 실패한 필드만 diskFreeBytes와
        // 같은 "-1=측정 불가" 관례로 표시하고 HTTP 스냅숏은 그대로 남긴다.
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
                safeCount("미처리 신고 수", () -> reportRepository.countByStatus(ReportStatus.PENDING)),
                http.slowRequests(),
                safeCount("세션 폐기 실패 수", () -> sessionRevocationTaskRepository.countByStatus(SessionRevocationTaskStatus.FAILED)),
                safeCount("이미지 삭제 backlog", () -> postImageRepository.countByStatus(PostImageStatus.PENDING_DELETE)),
                recommendationHistoryAgeHours(),
                recommendEnabled);
    }

    /**
     * 추천 이력 검증 기준이 마지막으로 갱신된 지 몇 시간 지났는지. 상태 행이 없거나
     * (V20 미적용) verifiedAt이 아직 한 번도 채워지지 않았으면 diskFreeBytes와 같은 관례로
     * -1(측정 불가)을 돌려준다.
     */
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

    /** @return 정상 조회 값. 실패하면 경고를 남기고 diskFreeBytes와 같은 관례로 -1을 돌려준다. */
    private long safeCount(String what, java.util.function.LongSupplier query) {
        try {
            return query.getAsLong();
        } catch (Exception e) {
            log.warn("{} 집계에 실패했습니다 — 이번 주기는 측정 불가(-1)로 남긴다.", what, e);
            return -1;
        }
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
     * <p>
     * 측정 자체가 불가능하면(존재하는 상위 경로를 하나도 못 찾음) {@code -1}을 돌려준다. 예전엔
     * 이 경우도 0을 돌려줬는데, 0은 "디스크가 실제로 가득 찼다"는 것과 구분이 안 됐다
     * (개선 보고서 "관측값의 경계와 의미") — {@link HealthSnapshot}의 판정이 {@code > 0}이라
     * 정작 가장 위험한 진짜 0바이트 상태를 조용히 건너뛰었다. {@code File.getUsableSpace()}
     * 자체도 JDK 차원에서 "0바이트"와 "조회 실패"를 구분하지 않는다는 잔여 한계는 남는다 —
     * 이 메서드가 할 수 있는 것은 최소한 "상위 경로를 못 찾은" 경우만이라도 구분하는 것이다.
     */
    private long usableSpace() {
        for (Path path = uploadDir; path != null; path = path.getParent()) {
            if (path.toFile().exists()) {
                return path.toFile().getUsableSpace();
            }
        }
        return -1;
    }
}
