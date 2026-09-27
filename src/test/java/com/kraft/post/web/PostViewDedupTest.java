package com.kraft.post.web;

import jakarta.servlet.http.Cookie;
import org.apache.tomcat.util.http.Rfc6265CookieProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PostViewDedup}이 새로고침·봇·재방문을 조회수 집계에서 걸러내는지 확인한다
 * (전체 리뷰 2026-09-26 A-BE-04 1단계).
 */
class PostViewDedupTest {

    private final PostViewDedup dedup = new PostViewDedup();
    private final Authentication user =
            new UsernamePasswordAuthenticationToken("1", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    private final Authentication anonymous =
            new AnonymousAuthenticationToken("key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    @DisplayName("봇 User-Agent는 로그인 여부와 무관하게 세지 않는다")
    void botUserAgent_neverCounts() {
        request.addHeader("User-Agent", "Mozilla/5.0 (compatible; Googlebot/2.1)");

        assertThat(dedup.shouldCount(request, response, 1L, user)).isFalse();
        assertThat(dedup.shouldCount(request, response, 1L, anonymous)).isFalse();
    }

    @Test
    @DisplayName("링크 미리보기(Slackbot 등 preview UA)도 세지 않는다")
    void previewUserAgent_doesNotCount() {
        request.addHeader("User-Agent", "Slackbot-LinkExpanding 1.0 (+https://api.slack.com/robots)");

        assertThat(dedup.shouldCount(request, response, 1L, anonymous)).isFalse();
    }

    @Test
    @DisplayName("로그인 사용자: 처음 보는 글은 센다")
    void loggedInUser_firstView_counts() {
        assertThat(dedup.shouldCount(request, response, 1L, user)).isTrue();
    }

    @Test
    @DisplayName("로그인 사용자: 같은 요청 흐름(같은 세션) 안에서 같은 글을 다시 열면 세지 않는다")
    void loggedInUser_sameSessionRevisit_doesNotCount() {
        assertThat(dedup.shouldCount(request, response, 1L, user)).isTrue();

        assertThat(dedup.shouldCount(request, response, 1L, user)).isFalse();
    }

    @Test
    @DisplayName("로그인 사용자: 다른 글은 별도로 센다")
    void loggedInUser_differentPost_countsIndependently() {
        dedup.shouldCount(request, response, 1L, user);

        assertThat(dedup.shouldCount(request, response, 2L, user)).isTrue();
    }

    @Test
    @DisplayName("로그인 사용자: 창(1시간)이 지난 방문은 다시 센다")
    void loggedInUser_afterWindowExpires_countsAgain() {
        request.getSession(true).setAttribute("kraft.viewedPosts",
                new java.util.HashMap<>(java.util.Map.of(1L, Instant.now().minus(PostViewDedup.WINDOW).minusSeconds(1))));

        assertThat(dedup.shouldCount(request, response, 1L, user)).isTrue();
    }

    @Test
    @DisplayName("익명 사용자: 처음 보는 글은 세고, 쿠키를 내려준다")
    void anonymousUser_firstView_countsAndSetsCookie() {
        assertThat(dedup.shouldCount(request, response, 1L, anonymous)).isTrue();

        Cookie cookie = response.getCookie("kraft_viewed");
        assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).startsWith("1:");
        assertThat(cookie.isHttpOnly()).isTrue();
    }

    @Test
    @DisplayName("익명 사용자: 방금 받은 쿠키를 그대로 다시 보내면 세지 않는다")
    void anonymousUser_revisitWithFreshCookie_doesNotCount() {
        request.setCookies(new Cookie("kraft_viewed", "1:" + Instant.now().getEpochSecond()));

        assertThat(dedup.shouldCount(request, response, 1L, anonymous)).isFalse();
    }

    @Test
    @DisplayName("익명 사용자: 쿠키의 방문 기록이 창(1시간)보다 오래됐으면 다시 센다")
    void anonymousUser_staleCookieEntry_countsAgain() {
        long staleEpoch = Instant.now().minus(PostViewDedup.WINDOW).minusSeconds(1).getEpochSecond();
        request.setCookies(new Cookie("kraft_viewed", "1:" + staleEpoch));

        assertThat(dedup.shouldCount(request, response, 1L, anonymous)).isTrue();
    }

    @Test
    @DisplayName("익명 사용자: 손상된 쿠키 값은 조용히 무시하고 새로 센다")
    void anonymousUser_malformedCookie_isIgnoredSafely() {
        request.setCookies(new Cookie("kraft_viewed", "not-a-valid-entry"));

        assertThat(dedup.shouldCount(request, response, 1L, anonymous)).isTrue();
    }

    @Test
    @DisplayName("익명 사용자가 여러 글을 열어도 실제 서버가 쿠키를 직렬화할 수 있다")
    void anonymousUser_multiplePosts_producesValidCookie() {
        dedup.shouldCount(request, response, 1L, anonymous);
        request.setCookies(response.getCookie("kraft_viewed"));
        response = new MockHttpServletResponse();

        assertThat(dedup.shouldCount(request, response, 2L, anonymous)).isTrue();
        Cookie cookie = response.getCookie("kraft_viewed");
        assertThat(new Rfc6265CookieProcessor().generateHeader(cookie, request))
                .contains("kraft_viewed=");

        request.setCookies(cookie);
        assertThat(dedup.shouldCount(request, new MockHttpServletResponse(), 1L, anonymous)).isFalse();
        assertThat(dedup.shouldCount(request, new MockHttpServletResponse(), 2L, anonymous)).isFalse();
    }

    @Test
    @DisplayName("쿠키의 시간이 Instant 범위를 벗어나면 해당 항목만 무시한다")
    void anonymousUser_outOfRangeTimestamp_isIgnoredSafely() {
        request.setCookies(new Cookie("kraft_viewed", "1:" + Long.MAX_VALUE));

        assertThat(dedup.shouldCount(request, response, 1L, anonymous)).isTrue();
    }

    @Test
    @DisplayName("authentication이 null이면(비로그인) 쿠키 경로로 처리한다")
    void nullAuthentication_isTreatedAsAnonymous() {
        assertThat(dedup.shouldCount(request, response, 1L, null)).isTrue();
        assertThat(response.getCookie("kraft_viewed")).isNotNull();
    }
}
