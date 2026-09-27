package com.kraft.observability;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 생존(liveness)과 준비(readiness)를 나눈 경량 헬스 엔드포인트.
 * <ul>
 *   <li>{@code /healthz} — 애플리케이션 컨텍스트가 떠서 요청을 받을 수 있는지만 본다(개선 보고서
 *       OPS-G5). DB 등 외부 의존성은 보지 않는다.</li>
 *   <li>{@code /readyz} — 여기에 더해 DB 커넥션을 얻어 검증까지 되는지 제한 시간 안에 본다.
 *       준비되면 200, 아니면 503(평가 보고서 2026-09-25 F04). 배포 스크립트
 *       ({@code deploy/deploy-apply.sh}의 {@code wait_for_health})가 새 jar와 롤백한 jar의 성공
 *       판정에 쓴다 — 예전에는 무조건 200인 {@code /healthz}로 판정해, DB에 붙지 못하는 jar도
 *       배포 성공으로 기록될 수 있었다.</li>
 * </ul>
 * 두 응답 모두 본문이 없다 — 실패 원인(자격 증명·호스트·드라이버 메시지)은 로그에만 남긴다.
 * 외부 HTTP 호출이나 추천 생성처럼 비싼 작업은 readiness에 넣지 않는다. 외부 스모크 테스트
 * ({@code build.yml}의 "Smoke test")는 이 엔드포인트가 아니라 홈({@code /})을 찌른다.
 * {@code SecurityConfig}가 모든 요청을 permitAll로 열어두므로 별도 보안 설정은 필요 없다.
 */
@Slf4j
@RestController
public class HealthController {

    private final DataSource dataSource;
    private final Duration timeout;
    // 커넥션 풀이 막혀 getConnection()이 풀 대기 시간만큼 멈춰도 요청 스레드는 제한 시간에
    // 돌려받는다. 가상 스레드라 멈춘 검사가 쌓여도 플랫폼 스레드를 잡아먹지 않는다.
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    // 동시에 들어온 /readyz 요청이 각자 새 DB 커넥션 검사를 띄우면, 검사 자체가 요청 수만큼
    // 풀을 잡아먹어 정작 확인하려던 "풀이 막혔는가"를 검사가 스스로 재현한다(전체 리뷰
    // 2026-09-26 BE-01). 진행 중인 검사가 있으면 새로 만들지 않고 그 결과를 함께 기다린다 —
    // 타임아웃도 각자 자기 것으로 재되, 공유 중인 future는 다른 대기자를 위해 취소하지 않는다.
    private final AtomicReference<CompletableFuture<Boolean>> inFlightCheck = new AtomicReference<>();

    public HealthController(DataSource dataSource,
                            @Value("${app.readiness.timeout:2s}") Duration timeout) {
        this.dataSource = dataSource;
        this.timeout = timeout;
    }

    @GetMapping("/healthz")
    public ResponseEntity<Void> healthz() {
        return ResponseEntity.ok().build();
    }

    @GetMapping("/readyz")
    public ResponseEntity<Void> readyz() {
        CompletableFuture<Boolean> check = currentOrNewCheck();
        try {
            if (check.get(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                return ResponseEntity.ok().build();
            }
        } catch (TimeoutException e) {
            log.warn("readiness: DB 확인이 {}ms 안에 끝나지 않았다", timeout.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("readiness: DB 확인 실패", e);
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }

    /**
     * 진행 중인 검사가 있으면 그것을 재사용하고, 없거나 이미 끝났으면 새로 시작한다. 새로
     * 시작한 검사는 끝나는 즉시 자기 자신을 참조에서 걷어 내(compareAndSet) 다음 요청이 새
     * 검사를 시작할 수 있게 한다 — 다른 검사가 이미 그 자리를 대체했다면 건드리지 않는다.
     */
    private CompletableFuture<Boolean> currentOrNewCheck() {
        return inFlightCheck.updateAndGet(existing -> {
            if (existing != null && !existing.isDone()) {
                return existing;
            }
            CompletableFuture<Boolean> next = CompletableFuture.supplyAsync(this::databaseReachable, executor);
            next.whenComplete((result, error) -> inFlightCheck.compareAndSet(next, null));
            return next;
        });
    }

    private boolean databaseReachable() {
        try (Connection connection = dataSource.getConnection()) {
            int seconds = (int) Math.max(1, timeout.toSeconds());
            if (connection.isValid(seconds)) {
                return true;
            }
            log.warn("readiness: DB 커넥션 검증 실패");
            return false;
        } catch (Exception e) {
            log.warn("readiness: DB 커넥션을 얻지 못했다: {}", e.getClass().getSimpleName());
            return false;
        }
    }
}
