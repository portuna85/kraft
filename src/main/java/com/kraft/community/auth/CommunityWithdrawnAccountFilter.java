package com.kraft.community.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kraft.common.error.ApiErrorResponse;
import com.kraft.community.user.CommunityUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

    // CommunityAuthEntryPoint와 동일한 이유로 앱 전역 ObjectMapper 빈에 의존하지 않는다.
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private final CommunityUserRepository communityUserRepository;

    public CommunityWithdrawnAccountFilter(CommunityUserRepository communityUserRepository) {
        this.communityUserRepository = communityUserRepository;
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
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            ApiErrorResponse body = new ApiErrorResponse(
                    Instant.now(),
                    HttpStatus.UNAUTHORIZED.value(),
                    HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                    "COMMUNITY_ACCOUNT_WITHDRAWN",
                    "탈퇴한 계정입니다.",
                    request.getRequestURI());
            objectMapper.writeValue(response.getWriter(), body);
            return;
        }
        chain.doFilter(request, response);
    }
}
