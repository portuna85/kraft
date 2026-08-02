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

/** Invalidates stale sessions whose community account was permanently deleted. */
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
                && !communityUserRepository.existsById(principal.getUserId())) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            SecurityContextHolder.clearContext();
            apiErrorResponseWriter.write(request, response, HttpStatus.UNAUTHORIZED,
                    "COMMUNITY_ACCOUNT_DELETED", "삭제된 계정입니다.");
            return;
        }
        chain.doFilter(request, response);
    }
}
