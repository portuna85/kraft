package com.kraft.community.auth;

import com.kraft.common.config.PublicBaseUrlProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * I-02/I-03: OAuth 로그인 성공·실패를 한 곳에서 처리한다({@link com.kraft.admin.AdminLoginHandler}와
 * 같은 패턴). 이전에는 {@code SimpleUrlAuthenticationSuccessHandler}/{@code
 * SimpleUrlAuthenticationFailureHandler}를 그대로 써서 성공·실패 모두 같은 URL로 리다이렉트할 뿐
 * 아무 신호도 남기지 않았다 — 실패는 완전히 무음이었고(I-02), 성공은 세션을 조회하지 않는
 * 공개 라우트(`/`)에 착지해 로그아웃 상태와 픽셀 단위로 같은 화면이었다(I-03).
 */
@Component
public class CommunityLoginHandler implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

    /**
     * 식별정보를 담지 않는 boolean 쿠키 — 닉네임·유저 ID는 절대 넣지 않는다.
     * {@code (public)} 셸이 세션 API를 부르지 않고도(불변식 I-4) 이 쿠키만 읽어 "로그인됨"을
     * 표시할 수 있게 한다. {@link CommunityAccountController#withdraw}와 로그아웃 양쪽에서 지운다.
     */
    static final String LOGGED_IN_COOKIE_NAME = "kraft_logged_in";

    private final PublicBaseUrlProperties publicBaseUrlProperties;
    private final MeterRegistry meterRegistry;

    public CommunityLoginHandler(PublicBaseUrlProperties publicBaseUrlProperties, MeterRegistry meterRegistry) {
        this.publicBaseUrlProperties = publicBaseUrlProperties;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        response.addHeader(HttpHeaders.SET_COOKIE, loggedInCookie(Duration.ofDays(365)).toString());
        response.sendRedirect(redirectTarget());
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        String reason = classify(exception);
        Counter.builder("kraft_community_login_failure_total")
                .description("커뮤니티 OAuth 로그인 실패 횟수(권한 요청 단계 — userinfo 단계 실패는 "
                        + "kraft_community_oauth_login_total{outcome=failure}가 별도로 센다)")
                .tag("reason", reason)
                .register(meterRegistry)
                .increment();
        response.sendRedirect(redirectTarget() + "?login_error=1&reason=" + reason);
    }

    // OAuth2AuthenticationException은 authorization-request 단계(세션 유실·state 불일치 등
    // 만료된 요청, provider 거부, 인앱 브라우저 UA 거부)에서 던져진다. userinfo 단계 실패는
    // CommunityOAuth2UserService가 이미 감싸 다시 던지므로 여기서는 그 원인이 그대로 보인다.
    private static String classify(AuthenticationException exception) {
        if (!(exception instanceof OAuth2AuthenticationException oauth2Exception)) {
            return "unknown";
        }
        String code = oauth2Exception.getError().getErrorCode();
        if (code == null) {
            return "unknown";
        }
        return switch (code) {
            case "access_denied" -> "access_denied";
            case "disallowed_useragent" -> "disallowed_useragent";
            case "invalid_state_parameter", "authorization_request_not_found" -> "session_expired";
            default -> "unknown";
        };
    }

    private static ResponseCookie loggedInCookie(Duration maxAge) {
        return ResponseCookie.from(LOGGED_IN_COOKIE_NAME, "1")
                .httpOnly(false)
                .secure(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    // 프런트(Next.js)가 별도로 존재하므로 로그인/로그아웃 완료 후에는 kraft.public-base-url로
    // 돌려보낸다 — 로컬/테스트처럼 값이 없으면 "/"로 대체.
    private String redirectTarget() {
        String publicBaseUrl = publicBaseUrlProperties.publicBaseUrl();
        return (publicBaseUrl == null || publicBaseUrl.isBlank()) ? "/" : publicBaseUrl;
    }
}
