package com.kraft.e2e;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * F03 회귀 테스트 전용 엔드포인트. 헤더는 즉시 보내고 본문은 끝내지 않는 응답을 실제로
 * 만들어야 http.js의 타임아웃이 헤더 수신 이후 본문 읽기까지 포함하는지 검증할 수 있다.
 * Playwright의 route.fulfill/continue는 부분 스트리밍 응답을 만들 수 없어(본문은 항상
 * 한 번에 완성된 값이어야 한다) 전용 엔드포인트로 재현한다. {@code @Profile("e2e")}가
 * 붙어 있어 운영·로컬에서는 빈 자체가 만들어지지 않는다.
 */
@Profile("e2e")
@RestController
public class E2eSlowResponseController {

    @GetMapping("/e2e/slow-body")
    public void slowBody(HttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(200);
        response.getWriter().write("{");
        response.flushBuffer();
        try {
            // 클라이언트의 기본 타임아웃(15초)보다 넉넉히 길게 연결을 열어 둔다.
            // 실제로는 클라이언트가 먼저 abort하므로 이 sleep이 끝까지 가지 않는다.
            Thread.sleep(30_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
