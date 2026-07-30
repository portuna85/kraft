package com.kraft.community.auth;

import com.kraft.community.user.CommunityWithdrawalService;
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
    public ResponseEntity<Void> withdraw(@AuthenticationPrincipal CommunityPrincipal principal,
                                          HttpServletRequest request, HttpServletResponse response) {
        communityWithdrawalService.withdraw(principal.getUserId());

        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        Cookie sessionCookie = new Cookie("JSESSIONID", "");
        sessionCookie.setPath("/");
        sessionCookie.setMaxAge(0);
        response.addCookie(sessionCookie);

        return ResponseEntity.noContent().build();
    }
}
