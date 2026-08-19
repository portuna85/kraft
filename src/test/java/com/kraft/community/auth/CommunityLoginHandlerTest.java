package com.kraft.community.auth;

import com.kraft.common.config.PublicBaseUrlProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * KF-26 UX-05(docs/improvement.md) — 로그인 실패 재시도가 항상 Google로 하드코딩돼
 * 있었다. 콜백 URI 마지막 세그먼트에서 실제 시도한 provider를 뽑아 리다이렉트에
 * 싣는지, 허용목록 밖 값은 반사하지 않는지 확인한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("커뮤니티 로그인 핸들러 — provider 반영")
class CommunityLoginHandlerTest {

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;

    private CommunityLoginHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CommunityLoginHandler(
                new PublicBaseUrlProperties("https://kraft.example"), new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("google 콜백 실패는 provider=google을 싣는다")
    void onAuthenticationFailure_googleCallback_addsGoogleProvider() throws Exception {
        when(request.getRequestURI()).thenReturn("/login/oauth2/code/google");

        handler.onAuthenticationFailure(request, response, accessDenied());

        verify(response).sendRedirect(
                "https://kraft.example?login_error=1&reason=access_denied&provider=google");
    }

    @Test
    @DisplayName("naver 콜백 실패는 provider=naver를 싣는다")
    void onAuthenticationFailure_naverCallback_addsNaverProvider() throws Exception {
        when(request.getRequestURI()).thenReturn("/login/oauth2/code/naver");

        handler.onAuthenticationFailure(request, response, accessDenied());

        verify(response).sendRedirect(
                "https://kraft.example?login_error=1&reason=access_denied&provider=naver");
    }

    @Test
    @DisplayName("허용목록 밖 세그먼트는 provider 파라미터 없이 리다이렉트한다")
    void onAuthenticationFailure_unknownSegment_omitsProvider() throws Exception {
        when(request.getRequestURI()).thenReturn("/login/oauth2/code/unexpected");

        handler.onAuthenticationFailure(request, response, accessDenied());

        verify(response).sendRedirect("https://kraft.example?login_error=1&reason=access_denied");
    }

    private static OAuth2AuthenticationException accessDenied() {
        return new OAuth2AuthenticationException(new OAuth2Error("access_denied"));
    }
}
