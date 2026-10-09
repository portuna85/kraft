package com.kraft.user.mail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/** 메일 본문의 1회용 토큰 링크 형식. 토큰은 서버 로그에 남지 않도록 프래그먼트(#)에 싣는다. */
class OutboxMailLinkTest {

    private static String body(OutboxMailKind kind, String token) {
        OutboxMailWorker worker = Mockito.mock(OutboxMailWorker.class, Mockito.CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(worker, "baseUrl", "https://kraft.example");
        return ReflectionTestUtils.invokeMethod(worker, "body", kind, token);
    }

    @Test
    @DisplayName("이메일 인증 링크는 토큰을 쿼리 문자열이 아니라 프래그먼트에 싣는다")
    void verifyEmail_putsTokenInFragment() {
        String body = body(OutboxMailKind.VERIFY_EMAIL, "abc123");

        assertThat(body).contains("https://kraft.example/users/verify#token=abc123");
        assertThat(body).doesNotContain("?token=");
    }

    @Test
    @DisplayName("비밀번호 재설정 링크도 프래그먼트를 쓴다")
    void passwordReset_putsTokenInFragment() {
        assertThat(body(OutboxMailKind.PASSWORD_RESET, "abc123"))
                .contains("https://kraft.example/users/password-reset#token=abc123");
    }
}
