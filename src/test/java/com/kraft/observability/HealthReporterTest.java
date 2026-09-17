package com.kraft.observability;

import com.kraft.user.domain.Role;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import com.kraft.user.mail.OutboxMailKind;
import com.kraft.report.domain.ReportRepository;
import com.kraft.user.mail.OutboxMailRepository;
import com.kraft.user.mail.OutboxMailStore;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 실제 DataSource·DB·디스크를 대상으로 상태를 모으고, 기준을 넘겼을 때 ERROR로 올리는지 본다
 * (개선 보고서 "관측 지표·알림" 공백).
 * <p>
 * Actuator를 되돌리지 않은 이유는 {@link HealthReporter} 주석에 있다 — 헬스 프로브를 찌를
 * 오케스트레이터가 없어 커밋 {@code 1080614}에서 제거됐고, 지금도 사정은 같다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class HealthReporterTest {

    @Autowired
    private HealthReporter healthReporter;

    @Autowired
    private RequestMetrics requestMetrics;

    @Autowired
    private OutboxMailStore outboxMailStore;

    @Autowired
    private OutboxMailRepository outboxMailRepository;

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MockMvc mockMvc;

    private ListAppender<ILoggingEvent> logs;
    private Logger observability;

    @BeforeEach
    void setUp() {
        outboxMailRepository.deleteAll();
        requestMetrics.drain();

        logs = new ListAppender<>();
        logs.start();
        observability = (Logger) LoggerFactory.getLogger("com.kraft.observability");
        observability.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        observability.detachAppender(logs);
        outboxMailRepository.deleteAll();
    }

    private ILoggingEvent onlyEvent() {
        assertThat(logs.list).hasSize(1);
        return logs.list.get(0);
    }

    @Test
    @DisplayName("정상일 때는 INFO로 평소 수치를 남긴다")
    void healthyStateIsLoggedAtInfo() {
        healthReporter.report(new HealthSnapshot(100, 1, 0, 90, 300, 1, 10, 0, 50_000_000_000L, 0, 0, 0));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getFormattedMessage()).contains("상태 정상").contains("요청=100");
    }

    /**
     * 이 테스트가 "알림"의 전부다. 외부 의존 없이, 임계를 넘긴 보고가 ERROR로 올라가야
     * logback 설정에 따라 {@code logs/kraft-error.log}에 함께 남는다.
     */
    @Test
    @DisplayName("기준을 넘기면 ERROR로 올려 무엇이 넘었는지 함께 남긴다")
    void breachIsEscalatedToError() {
        healthReporter.report(new HealthSnapshot(100, 40, 3, 90, 300, 1, 10, 0, 50_000_000_000L, 0, 0, 0));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).as("ERROR라야 kraft-error.log에 모인다").isEqualTo(Level.ERROR);
        assertThat(event.getFormattedMessage())
                .contains("상태 이상")
                .contains("HTTP 오류율")
                .contains("5xx 3건")
                .as("무엇이 넘었는지와 함께 그 주기의 전체 수치도 있어야 판단할 수 있다")
                .contains("요청=100");
    }

    @Test
    @DisplayName("실제 커넥션 풀·디스크·메일 대기열을 읽어 온다")
    void collectReadsRealInfrastructure() {
        User user = userRepository.save(User.builder()
                .name("metrics-" + UUID.randomUUID().toString().substring(0, 8))
                .email("metrics-" + UUID.randomUUID() + "@example.com")
                .password("encoded")
                .role(Role.GUEST)
                .build());
        outboxMailStore.enqueue(user, UUID.randomUUID().toString(), OutboxMailKind.VERIFY_EMAIL);

        HealthSnapshot snapshot = healthReporter.collect();

        assertThat(snapshot.poolTotal()).as("Hikari 풀 크기를 읽지 못하면 사용률이 늘 0이 된다").isPositive();
        assertThat(snapshot.diskFreeBytes())
                .as("업로드 디렉터리가 아직 없어도 상위 경로로 거슬러 올라가 읽는다").isPositive();
        assertThat(snapshot.mailPending()).isEqualTo(1);
        assertThat(snapshot.mailFailed()).isZero();
    }

    /**
     * 필터가 보안 체인보다 앞에 있어야 의미가 있다. 뒤에 있으면 인증 없이 들어온 API 호출처럼
     * 필터 단계에서 끝나는 응답이 통째로 빠지고, 세션이 깨져 오류가 쏟아질 때 오히려 지표가
     * 조용해진다.
     */
    @Test
    @DisplayName("보안 필터가 먼저 거절한 요청도 통계에 잡힌다")
    void requestsRejectedBySecurityAreStillCounted() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"));

        HealthSnapshot snapshot = healthReporter.collect();
        assertThat(snapshot.requests()).isPositive();
    }

    /**
     * O04: drain()이 이미 비워 버린 HTTP 스냅숏을, 그 뒤에 도는 DB 집계 조회 하나의 실패로
     * 통째로 잃던 문제를 고친다. 이제 DB 집계는 항목별로 감싸져 있어, 실패한 항목만
     * diskFreeBytes와 같은 "-1=측정 불가"로 남고 HTTP 스냅숏은 그대로 보고된다.
     */
    @Test
    @DisplayName("DB 집계 하나가 실패해도 이미 확보한 HTTP 스냅숏은 그대로 보고된다")
    void dbAggregationFailureDoesNotLoseTheHttpSnapshot() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")); // HTTP 스냅숏에 요청 하나를 남긴다.

        OutboxMailRepository failing = mock(OutboxMailRepository.class);
        given(failing.countByStatus(any())).willThrow(new RuntimeException("DB가 응답하지 않습니다"));
        HealthReporter broken = new HealthReporter(requestMetrics, failing, reportRepository, null, "uploads/images");
        ReflectionTestUtils.setField(broken, "enabled", true);

        assertThatCode(broken::report).doesNotThrowAnyException();

        // 집계 실패 경고 한 줄 + 실제 보고 한 줄, 최소 둘은 남아야 한다 — 실패했다는 사실 자체가
        // 사라지면 안 된다.
        assertThat(logs.list).hasSizeGreaterThanOrEqualTo(2);
        ILoggingEvent reportEvent = logs.list.get(logs.list.size() - 1);
        assertThat(reportEvent.getFormattedMessage())
                .as("HTTP 스냅숏은 DB 집계 실패와 무관하게 그대로 보고에 남아야 한다")
                .contains("요청=1")
                .as("실패한 항목은 diskFreeBytes와 같은 관례로 -1(측정 불가)로 남는다")
                .contains("메일대기=-1");
    }

    /**
     * 관측이 서비스를 막으면 안 되지만, 조용히 멈추면 "지표가 없는데 아무도 모르는" 상태가 된다.
     * DB 집계 실패는 이제 collect() 안에서 흡수되므로, 바깥 report()의 방어가 실제로 남아
     * 쓰이는 경우는 그보다 더 근본적인 실패(예: RequestMetrics 자체의 결함)뿐이다 — 그런
     * 상황까지 서비스를 막지 않고 실패 자체를 남기는지 확인한다.
     */
    @Test
    @DisplayName("collect() 바깥의 예기치 못한 실패도 서비스를 막지 않되 실패를 남긴다")
    void unexpectedFailureOutsideCollectIsLogged() {
        RequestMetrics failingMetrics = mock(RequestMetrics.class);
        given(failingMetrics.drain()).willThrow(new RuntimeException("지표 수집기 자체가 깨졌습니다"));
        HealthReporter broken = new HealthReporter(failingMetrics, outboxMailRepository, reportRepository, null, "uploads/images");
        ReflectionTestUtils.setField(broken, "enabled", true);

        assertThatCode(broken::report).doesNotThrowAnyException();

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getFormattedMessage()).contains("상태 점검에 실패");
    }

    /**
     * Hikari가 아닌 풀로 바뀌거나 풀 정보를 읽을 수 없어도 보고는 계속되어야 한다.
     * 읽지 못한 항목은 0으로 남고, 0인 항목은 임계 판정에서 빠진다(없는 값으로 경보할 수 없다).
     */
    @Test
    @DisplayName("커넥션 풀 정보를 읽을 수 없으면 그 항목만 빼고 보고한다")
    void unknownPoolIsSkippedRatherThanFailing() {
        HealthReporter noPool = new HealthReporter(requestMetrics, outboxMailRepository, reportRepository, null, "uploads/images");

        HealthSnapshot snapshot = noPool.collect();

        assertThat(snapshot.poolTotal()).isZero();
        assertThat(snapshot.poolUsage()).isZero();
        assertThat(snapshot.breaches(healthReporter.thresholds()))
                .noneMatch(line -> line.startsWith("DB 커넥션"));
    }

    @Test
    @DisplayName("설정한 임계값이 실제 판정에 쓰인다")
    void configuredThresholdsAreUsed() {
        HealthThresholds limits = healthReporter.thresholds();

        assertThat(limits.minRequests()).isEqualTo(20);
        assertThat(limits.errorRate()).isEqualTo(0.1);
        assertThat(limits.serverErrors()).isZero();
        assertThat(List.of(limits.avgMillis(), limits.diskFreeBytes())).doesNotContain(0L);
    }
}
