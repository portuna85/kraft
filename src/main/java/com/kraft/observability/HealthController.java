package com.kraft.observability;

import jakarta.servlet.http.HttpServletRequest;
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
 * 생존(liveness)과 준비(readiness)를 나눈 경량 헬스 엔드포인트. 본문은 없고 실패 원인은 로그에만 남긴다.
 * <ul>
 *   <li>{@code /healthz} — 컨텍스트가 떠서 요청을 받을 수 있는지만 본다(DB 등 외부 의존성은 보지 않는다).</li>
 *   <li>{@code /readyz} — 더해서 DB 커넥션을 제한 시간 안에 검증한다(200/503). 배포 스크립트
 *       ({@code deploy/deploy-apply.sh}의 {@code wait_for_health})가 새 jar와 롤백한 jar의 성공 판정에 쓴다.</li>
 * </ul>
 * {@code /readyz}는 루프백에서만 응답한다 — 배포 스크립트만 {@code 127.0.0.1}로 부르고, 외부에 열면 반복 호출로
 * 풀을 점유하거나 응답 코드로 DB 장애를 드러낼 수 있다({@code forward-headers-strategy: native}라 프록시 뒤에서도
 * {@code getRemoteAddr()}가 실제 IP다). 외부 HTTP 호출이나 추천 생성 같은 비싼 작업은 readiness에 넣지 않으며,
 * 외부 스모크 테스트({@code build.yml})는 {@code /recommend}를 찌른다.
 */
@Slf4j
@RestController
public class HealthController {

    static final String BUILD_HEADER = "X-Kraft-Build";

    private final DataSource dataSource;
    private final Duration timeout;
    // getConnection()이 풀 대기로 멈춰도 요청 스레드는 제한 시간에 돌려받는다(가상 스레드라 멈춘 검사가 쌓여도 부담이 없다).
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    // 동시 /readyz 요청이 각자 검사를 띄우면 검사가 풀을 잡아먹어 "풀이 막혔는가"를 스스로 재현한다.
    // 진행 중인 검사가 있으면 결과를 함께 기다린다(타임아웃은 각자, 공유 future는 취소하지 않는다).
    private final AtomicReference<CompletableFuture<Boolean>> inFlightCheck = new AtomicReference<>();

    private final String buildVersion;

    public HealthController(DataSource dataSource,
                            @Value("${app.readiness.timeout:2s}") Duration timeout,
                            @Value("${app.build-version:unknown}") String buildVersion) {
        this.dataSource = dataSource;
        this.timeout = timeout;
        this.buildVersion = buildVersion;
    }

    /** 본문 없이 떠 있는 jar의 빌드(커밋)만 {@code X-Kraft-Build}로 알린다 — 배포 후 스모크 테스트가 "방금 푸시한 빌드가 응답한다"를 확인한다. */
    @GetMapping("/healthz")
    public ResponseEntity<Void> healthz() {
        return ResponseEntity.ok().header(BUILD_HEADER, buildVersion).build();
    }

    @GetMapping("/readyz")
    public ResponseEntity<Void> readyz(HttpServletRequest request) {
        if (!isLoopback(request.getRemoteAddr())) {
            // 존재를 알리지 않으려 403이 아니라 404.
            return ResponseEntity.notFound().build();
        }
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

    /** 진행 중인 검사를 재사용하고, 없거나 끝났으면 새로 시작한다. 끝나면 자기 자신을 걷어 낸다(그 사이 다른 검사가 대체했다면 건드리지 않는다). */
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

    private static boolean isLoopback(String remoteAddr) {
        return "127.0.0.1".equals(remoteAddr) || "0:0:0:0:0:0:0:1".equals(remoteAddr) || "::1".equals(remoteAddr);
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
