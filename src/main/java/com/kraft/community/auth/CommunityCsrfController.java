package com.kraft.community.auth;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * BE-CSRF-01(docs/improvement.md): 익명 방문자용 경량 CSRF 부트스트랩.
 *
 * {@code CommunitySessionController}는 신원 조회(로그인 여부)와 CSRF 쿠키 발급을
 * 동시에 겸했다 — CSRF 쿠키는 이 컨트롤러가 직접 발급하는 게 아니라
 * {@code CommunitySecurityConfig}의 {@code csrfCookieFilter}가 {@code /api/v1/
 * community/**} 안의 모든 요청에 부수효과로 발급하는 것이라, 신원 조회 없는
 * endpoint도 이 자리에만 있으면 똑같이 쿠키를 받는다. {@code @AuthenticationPrincipal}을
 * 아예 받지 않아 신원 조회 자체가 없고, {@code CookieCsrfTokenRepository}는 세션이
 * 아니라 쿠키 자체에 토큰을 담으므로 {@code HttpSession}도 만들지 않는다.
 *
 * FE-SEC-02가 익명 방문자에게 불필요한 {@code /community/session} 호출을 줄이려
 * 시도했다가, 그 호출이 CSRF 쿠키 발급의 유일한 경로였음을 뒤늦게 발견하고
 * 원복했다 — 이 endpoint가 그 대체 경로다(프론트 연결은 FE-SEC-02 재개 시 별도로 한다).
 */
@RestController
@RequestMapping("/api/v1/community/csrf")
public class CommunityCsrfController {

    @GetMapping
    public ResponseEntity<Void> bootstrap() {
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
