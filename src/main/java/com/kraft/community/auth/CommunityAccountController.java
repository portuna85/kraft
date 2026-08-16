package com.kraft.community.auth;

import com.kraft.community.user.CommunityWithdrawalService;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * KB-04: 탈퇴 엔드포인트. 요청을 보낸 세션 본인의 탈퇴이므로 이 요청 안에서 즉시 세션을
 * 무효화한다 — CommunityWithdrawnAccountFilter는 그 이후의 다른 요청/다른 기기 세션을 막는다.
 */
@RestController
@RequestMapping("/api/v1/community/me")
public class CommunityAccountController {

    private final CommunityWithdrawalService communityWithdrawalService;

    public CommunityAccountController(CommunityWithdrawalService communityWithdrawalService) {
        this.communityWithdrawalService = communityWithdrawalService;
    }

    @PostMapping("/withdrawal")
    @ApiResponse(responseCode = "204")
    public ResponseEntity<Void> withdraw(@AuthenticationPrincipal CommunityPrincipal principal,
                                          HttpServletRequest request, HttpServletResponse response) {
        communityWithdrawalService.withdraw(principal.getUserId());

        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        // CodeQL java/insecure-cookie: 이 쿠키는 스프링이 관리하는 실제 세션 쿠키
        // (server.servlet.session.cookie.*)를 거치지 않는 수동 생성 객체라 secure 플래그를
        // 자동으로 물려받지 못한다. HTTPS 배포가 기본 전제이므로 secure는 조건 없이 true —
        // Chrome/Firefox 모두 localhost는 신뢰 출처로 취급해 로컬 HTTP 개발에도 지장이 없다.
        Cookie sessionCookie = new Cookie("JSESSIONID", "");
        sessionCookie.setPath("/");
        sessionCookie.setMaxAge(0);
        sessionCookie.setHttpOnly(true);
        sessionCookie.setSecure(true);
        response.addCookie(sessionCookie);

        // I-03: 탈퇴도 로그아웃과 마찬가지로 kraft_logged_in을 지운다 — 안 지우면 탈퇴 후에도
        // 공개 라우트가 "로그인됨"으로 표시된다. CommunityLoginHandler가 심을 때와 같은
        // 속성(HttpOnly=false)이라 CookieClearingLogoutHandler 방식(jakarta Cookie, HttpOnly
        // 미설정 시 기본 false)으로 지운다.
        Cookie loggedInCookie = new Cookie("kraft_logged_in", "");
        loggedInCookie.setPath("/");
        loggedInCookie.setMaxAge(0);
        loggedInCookie.setSecure(true);
        response.addCookie(loggedInCookie);

        return ResponseEntity.noContent().build();
    }
}
