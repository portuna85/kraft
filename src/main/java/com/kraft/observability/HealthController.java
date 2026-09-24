package com.kraft.observability;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 배포 스크립트({@code deploy/deploy-apply.sh}의 {@code wait_for_health})와 외부 스모크
 * 테스트({@code build.yml}의 "Smoke test")가 찌르는 경량 헬스 엔드포인트(개선 보고서 OPS-G5).
 * <p>
 * 예전에는 이 둘이 {@code GET /}을 찔렀다 — 게시글 목록 전체를 DB에서 조회하고 템플릿을
 * 렌더링해야 응답이 오므로, 재시작 직후 최대 30회 반복되는 이 루프가 매번 불필요하게 무거운
 * 요청을 만들었다. 여기는 애플리케이션 컨텍스트가 떠서 요청을 받을 수 있는지만 본다 — DB나
 * 다른 외부 의존성 상태는 보지 않는다(그건 {@link HealthReporter}가 별도로 로그에 남긴다).
 * {@code SecurityConfig}가 모든 요청을 permitAll로 열어두므로 별도 보안 설정은 필요 없다.
 */
@RestController
public class HealthController {

    @GetMapping("/healthz")
    public ResponseEntity<Void> healthz() {
        return ResponseEntity.ok().build();
    }
}
