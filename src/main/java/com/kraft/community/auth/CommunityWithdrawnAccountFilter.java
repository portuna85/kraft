package com.kraft.community.auth;

import com.kraft.common.web.ApiErrorResponseWriter;
import com.kraft.community.user.CommunityUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * KB-04: 세션이 살아있는 동안 계정이 탈퇴 처리될 수 있으므로(다른 기기 세션·탈퇴 처리 자체가
 * 진행 중인 현재 세션 모두), 매 요청마다 principal의 탈퇴 여부를 DB에서 확인한다. 탈퇴
 * 계정이면 세션을 무효화하고 401로 응답해 강제 로그아웃시킨다 — CommunitySecurityConfig가
 * AuthorizationFilter 앞에 등록해 인가 판단보다 먼저 걸러진다.
 */
public class CommunityWithdrawnAccountFilter extends OncePerRequestFilter {

    private final CommunityUserRepository communityUserRepository;
    private final ApiErrorResponseWriter apiErrorResponseWriter;

    public CommunityWithdrawnAccountFilter(CommunityUserRepository communityUserRepository,
                                            ApiErrorResponseWriter apiErrorResponseWriter) {
        this.communityUserRepository = communityUserRepository;
        this.apiErrorResponseWriter = apiErrorResponseWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CommunityPrincipal principal
                && communityUserRepository.existsByIdAndWithdrawnAtIsNotNull(principal.getUserId())) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            SecurityContextHolder.clearContext();
            apiErrorResponseWriter.write(request, response, HttpStatus.UNAUTHORIZED,
                    "COMMUNITY_ACCOUNT_WITHDRAWN", "탈퇴한 계정입니다.");
            return;
        }
        chain.doFilter(request, response);
    }
}
