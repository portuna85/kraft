package com.kraft.community.auth;

import com.kraft.common.web.ApiErrorResponseWriter;
import com.kraft.community.user.CommunityUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Invalidates stale sessions whose community account was permanently deleted.
 *
 * <p>BE-PERF-02(docs/improvement.md): this used to run on every authenticated request in this
 * filter chain (GET included) before {@code AuthorizationFilter}, adding one {@code existsById}
 * SELECT to the hot read path for a rare event (withdrawal). GET/HEAD/OPTIONS are now excluded
 * via {@link #shouldNotFilter} — a stale session can keep reading with a deleted account's
 * cached principal until it attempts a state change, at which point this filter still runs and
 * both invalidates the session and returns {@code COMMUNITY_ACCOUNT_DELETED}. This is an
 * accepted trade-off (option (b) in the doc), not an oversight: reads from an already-stale
 * session carry no integrity risk (no state changes), only a bounded staleness window until the
 * next write attempt.
 */
public class CommunityWithdrawnAccountFilter extends OncePerRequestFilter {

    private static final Set<String> SKIPPED_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final CommunityUserRepository communityUserRepository;
    private final ApiErrorResponseWriter apiErrorResponseWriter;

    public CommunityWithdrawnAccountFilter(CommunityUserRepository communityUserRepository,
                                            ApiErrorResponseWriter apiErrorResponseWriter) {
        this.communityUserRepository = communityUserRepository;
        this.apiErrorResponseWriter = apiErrorResponseWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return SKIPPED_METHODS.contains(request.getMethod());
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
