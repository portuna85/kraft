package com.kraft.observability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * readiness 판정 규칙(평가 보고서 2026-09-25 F04). 배포·롤백 성공 판정에 쓰이므로 DB에 붙지
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
    @DisplayName("readyz: DB 커넥션을 얻고 검증되면 200, 본문 없음")
    void readyz_whenDatabaseValid_isOk() throws Exception {
        given(dataSource.getConnection()).willReturn(connection);
        given(connection.isValid(anyInt())).willReturn(true);

        ResponseEntity<Void> response = controller(Duration.ofSeconds(2)).readyz();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNull();
    }

    @Test
    @DisplayName("readyz: 커넥션을 얻지 못하면 503이고 오류 내용을 응답에 싣지 않는다")
    void readyz_whenConnectionFails_isUnavailable() throws Exception {
        given(dataSource.getConnection()).willThrow(new SQLException("Access denied for user 'kraft'@'10.0.0.1'"));

        ResponseEntity<Void> response = controller(Duration.ofSeconds(2)).readyz();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNull();
    }

    @Test
    @DisplayName("readyz: 커넥션 검증이 실패하면 503")
    void readyz_whenConnectionInvalid_isUnavailable() throws Exception {
        given(dataSource.getConnection()).willReturn(connection);
        given(connection.isValid(anyInt())).willReturn(false);

        assertThat(controller(Duration.ofSeconds(2)).readyz().getStatusCode())
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
        ResponseEntity<Void> response = controller(Duration.ofMillis(300)).readyz();
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(elapsedMillis).isLessThan(3_000);
    }

    private HealthController controller(Duration timeout) {
        return new HealthController(dataSource, timeout);
    }
}
