package com.kraft.shared.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 전체 리뷰 2026-09-26 A-SEC-06: 게시글·댓글·업로드·검색의 계정(검색은 IP) 기준 속도
 * 제한. 창 길이(분·시간)까지 실제로 기다리는 테스트는 하지 않는다 — 그 규칙은
 * {@link FixedWindowRateLimiter} 자체의 테스트가 이미 검증한다. 여기서는 "몇 번째부터
 * 막히는가"와 "관리자는 예외인가"만 확인한다.
 */
class WriteRateLimitersTest {

    private static Authentication userAuth(String userId) {
        return new UsernamePasswordAuthenticationToken(userId, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private static Authentication adminAuth(String userId) {
        return new UsernamePasswordAuthenticationToken(userId, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private WriteRateLimiters limitersWith(int postPerMinute, int postPerHour, int comment,
                                            int upload, int search) {
        return new WriteRateLimiters(true, postPerMinute, postPerHour, comment, upload, search);
    }

    @Test
    @DisplayName("게시글: 분당 한도를 넘으면 거절한다")
    void post_rejectsAfterPerMinuteLimit() {
        WriteRateLimiters limiters = limitersWith(3, 20, 10, 10, 60);
        Authentication auth = userAuth("1");

        assertThat(limiters.tryAcquirePost(auth)).isTrue();
        assertThat(limiters.tryAcquirePost(auth)).isTrue();
        assertThat(limiters.tryAcquirePost(auth)).isTrue();
        assertThat(limiters.tryAcquirePost(auth)).isFalse();
    }

    @Test
    @DisplayName("게시글: 분당 한도보다 시간당 한도가 먼저 차면 그 시점부터 거절한다")
    void post_rejectsAfterPerHourLimitEvenWithinPerMinuteLimit() {
        // 분당 한도를 시간당보다 넉넉히 둬, 시간당 한도만으로 막히는지 본다.
        WriteRateLimiters limiters = limitersWith(100, 2, 10, 10, 60);
        Authentication auth = userAuth("1");

        assertThat(limiters.tryAcquirePost(auth)).isTrue();
        assertThat(limiters.tryAcquirePost(auth)).isTrue();
        assertThat(limiters.tryAcquirePost(auth)).isFalse();
    }

    @Test
    @DisplayName("계정별로 독립된 한도를 쓴다")
    void post_tracksLimitsPerAccountIndependently() {
        WriteRateLimiters limiters = limitersWith(1, 20, 10, 10, 60);

        assertThat(limiters.tryAcquirePost(userAuth("1"))).isTrue();
        assertThat(limiters.tryAcquirePost(userAuth("1"))).isFalse();
        // 다른 계정은 영향받지 않는다.
        assertThat(limiters.tryAcquirePost(userAuth("2"))).isTrue();
    }

    @Test
    @DisplayName("관리자는 모든 제한에서 제외된다")
    void adminIsExemptFromEveryLimit() {
        WriteRateLimiters limiters = limitersWith(1, 1, 1, 1, 60);
        Authentication admin = adminAuth("9");

        for (int i = 0; i < 5; i++) {
            assertThat(limiters.tryAcquirePost(admin)).isTrue();
            assertThat(limiters.tryAcquireComment(admin)).isTrue();
            assertThat(limiters.tryAcquireUpload(admin)).isTrue();
        }
    }

    @Test
    @DisplayName("enabled=false면 모든 제한을 건너뛴다")
    void disabled_bypassesEveryLimit() {
        WriteRateLimiters limiters = new WriteRateLimiters(false, 1, 1, 1, 1, 1);
        Authentication auth = userAuth("1");

        for (int i = 0; i < 5; i++) {
            assertThat(limiters.tryAcquirePost(auth)).isTrue();
            assertThat(limiters.tryAcquireComment(auth)).isTrue();
            assertThat(limiters.tryAcquireUpload(auth)).isTrue();
            assertThat(limiters.tryAcquireSearch("203.0.113.1")).isTrue();
        }
    }

    @Test
    @DisplayName("댓글: 분당 한도를 넘으면 거절한다")
    void comment_rejectsAfterPerMinuteLimit() {
        WriteRateLimiters limiters = limitersWith(3, 20, 2, 10, 60);
        Authentication auth = userAuth("1");

        assertThat(limiters.tryAcquireComment(auth)).isTrue();
        assertThat(limiters.tryAcquireComment(auth)).isTrue();
        assertThat(limiters.tryAcquireComment(auth)).isFalse();
    }

    @Test
    @DisplayName("업로드: 분당 한도를 넘으면 거절한다")
    void upload_rejectsAfterPerMinuteLimit() {
        WriteRateLimiters limiters = limitersWith(3, 20, 10, 2, 60);
        Authentication auth = userAuth("1");

        assertThat(limiters.tryAcquireUpload(auth)).isTrue();
        assertThat(limiters.tryAcquireUpload(auth)).isTrue();
        assertThat(limiters.tryAcquireUpload(auth)).isFalse();
    }

    @Test
    @DisplayName("검색: IP 기준으로 분당 한도를 넘으면 거절한다")
    void search_rejectsAfterPerMinuteLimitByIp() {
        WriteRateLimiters limiters = limitersWith(3, 20, 10, 10, 2);

        assertThat(limiters.tryAcquireSearch("203.0.113.1")).isTrue();
        assertThat(limiters.tryAcquireSearch("203.0.113.1")).isTrue();
        assertThat(limiters.tryAcquireSearch("203.0.113.1")).isFalse();
        // 다른 IP는 영향받지 않는다.
        assertThat(limiters.tryAcquireSearch("198.51.100.7")).isTrue();
    }
}
