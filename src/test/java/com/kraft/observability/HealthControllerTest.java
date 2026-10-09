package com.kraft.observability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * readiness 판정 규칙. 배포·롤백 성공 판정에 쓰이므로 DB에 붙지
 * 못하거나 응답이 멈춘 상태를 200으로 돌려주면 안 된다.
 */
class HealthControllerTest {

    private final DataSource dataSource = mock(DataSource.class);
    private final Connection connection = mock(Connection.class);

    @Test
    @DisplayName("healthz는 DB 상태와 무관하게 200이다(liveness)")
    void healthz_isAlwaysOk() throws Exception {
        given(dataSource.getConnection()).willThrow(new SQLException("down"));

        assertThat(controller(Duration.ofSeconds(2)).healthz().getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("healthz는 떠 있는 jar의 빌드를 X-Kraft-Build 헤더로 알린다(OPS-12)")
    void healthz_exposesBuildVersionHeader() {
        assertThat(controller(Duration.ofSeconds(2)).healthz().getHeaders().getFirst("X-Kraft-Build"))
                .isEqualTo("abc1234");
    }

    @Test
    @DisplayName("readyz: DB 커넥션을 얻고 검증되면 200, 본문 없음")
    void readyz_whenDatabaseValid_isOk() throws Exception {
        given(dataSource.getConnection()).willReturn(connection);
        given(connection.isValid(anyInt())).willReturn(true);

        ResponseEntity<Void> response = controller(Duration.ofSeconds(2)).readyz(loopbackRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNull();
    }

    @Test
    @DisplayName("readyz: 커넥션을 얻지 못하면 503이고 오류 내용을 응답에 싣지 않는다")
    void readyz_whenConnectionFails_isUnavailable() throws Exception {
        given(dataSource.getConnection()).willThrow(new SQLException("Access denied for user 'kraft'@'10.0.0.1'"));

        ResponseEntity<Void> response = controller(Duration.ofSeconds(2)).readyz(loopbackRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNull();
    }

    @Test
    @DisplayName("readyz: 커넥션 검증이 실패하면 503")
    void readyz_whenConnectionInvalid_isUnavailable() throws Exception {
        given(dataSource.getConnection()).willReturn(connection);
        given(connection.isValid(anyInt())).willReturn(false);

        assertThat(controller(Duration.ofSeconds(2)).readyz(loopbackRequest()).getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("readyz: 커넥션 획득이 멈추면 제한 시간에 503으로 돌아온다")
    void readyz_whenConnectionHangs_returnsWithinTimeout() throws Exception {
        given(dataSource.getConnection()).willAnswer(invocation -> {
            Thread.sleep(10_000);
            return connection;
        });

        long startedAt = System.nanoTime();
        ResponseEntity<Void> response = controller(Duration.ofMillis(300)).readyz(loopbackRequest());
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(elapsedMillis).isLessThan(3_000);
    }

    @Test
    @DisplayName("readyz: 동시에 들어온 요청은 새 DB 확인을 각자 띄우지 않고 진행 중인 검사를 공유한다")
    void readyz_concurrentRequests_shareSingleDatabaseCheck() throws Exception {
        CountDownLatch connectionCallStarted = new CountDownLatch(1);
        CountDownLatch releaseConnection = new CountDownLatch(1);
        given(dataSource.getConnection()).willAnswer(invocation -> {
            connectionCallStarted.countDown();
            releaseConnection.await(5, TimeUnit.SECONDS);
            return connection;
        });
        given(connection.isValid(anyInt())).willReturn(true);

        HealthController controller = controller(Duration.ofSeconds(5));
        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            Future<ResponseEntity<Void>> first = callers.submit(() -> controller.readyz(loopbackRequest()));
            // 두 번째 요청이 첫 번째 검사가 아직 끝나지 않은 시점에 들어오도록, 커넥션 호출이
            // 시작될 때까지는 기다리되 끝나기 전에(래치를 아직 풀지 않은 채) 제출한다.
            connectionCallStarted.await(1, TimeUnit.SECONDS);
            java.util.concurrent.atomic.AtomicReference<Thread> secondThread = new java.util.concurrent.atomic.AtomicReference<>();
            Future<ResponseEntity<Void>> second = callers.submit(() -> {
                secondThread.set(Thread.currentThread());
                return controller.readyz(loopbackRequest());
            });
            // 두 번째 요청이 진행 중인 검사를 기다리는 상태(future.get)에 들어갈 때까지 기다린다. 고정 sleep 대신
            // 스레드 상태를 본다(OPS-35).
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5)).until(() ->
                    secondThread.get() != null && secondThread.get().getState() == Thread.State.TIMED_WAITING);
            releaseConnection.countDown();

            assertThat(first.get(5, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(second.get(5, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.OK);
        } finally {
            callers.shutdownNow();
        }

        verify(dataSource, times(1)).getConnection();
    }

    /** A-SEC-04: 루프백이 아니면 검사조차 하지 않고 404를 준다. */
    @ParameterizedTest(name = "원격 주소 [{0}]는 404다")
    @ValueSource(strings = {"203.0.113.7", "10.0.0.5", "::ffff:127.0.0.1"})
    @DisplayName("readyz: 루프백이 아닌 요청은 404이고 DB를 확인하지 않는다")
    void readyz_whenNotLoopback_returnsNotFoundWithoutCheckingDatabase(String remoteAddr) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);

        ResponseEntity<Void> response = controller(Duration.ofSeconds(2)).readyz(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(dataSource, org.mockito.Mockito.never()).getConnection();
    }

    private static MockHttpServletRequest loopbackRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        return request;
    }

    private HealthController controller(Duration timeout) {
        return new HealthController(dataSource, timeout, "abc1234");
    }
}
