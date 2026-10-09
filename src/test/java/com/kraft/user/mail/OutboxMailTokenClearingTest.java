package com.kraft.user.mail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code outbox_mails.token}은 발송 전까지만 평문으로 있어야 한다(V24의 알려진 한계). 끝난 행
 * (SENT/FAILED/STALE)에 평문이 보관 기간 동안 남지 않는다는 불변 조건을 고정한다.
 */
class OutboxMailTokenClearingTest {

    private static OutboxMail mail() {
        return OutboxMail.builder().token("plain-token-value").kind(OutboxMailKind.PASSWORD_RESET).build();
    }

    @Test
    @DisplayName("발송에 성공하면 평문 토큰을 지운다")
    void markSent_clearsToken() {
        OutboxMail mail = mail();
        mail.markSending();

        mail.markSent();

        assertThat(mail.getToken()).isNull();
        assertThat(mail.getStatus()).isEqualTo(OutboxMailStatus.SENT);
    }

    @Test
    @DisplayName("재시도 기회가 남은 실패는 토큰을 유지하고, 마지막 실패는 지운다")
    void markFailed_keepsTokenWhileRetryable_clearsOnFinalFailure() {
        OutboxMail mail = mail();
        mail.markSending();
        mail.markFailed("smtp down", 3);

        assertThat(mail.getStatus()).isEqualTo(OutboxMailStatus.PENDING);
        assertThat(mail.getToken()).isEqualTo("plain-token-value");

        mail.markSending();
        mail.markSending();
        mail.markFailed("smtp down", 3);

        assertThat(mail.getStatus()).isEqualTo(OutboxMailStatus.FAILED);
        assertThat(mail.getToken()).isNull();
    }

    @Test
    @DisplayName("옛 토큰이라 건너뛴 메일도 토큰을 지운다")
    void markStale_clearsToken() {
        OutboxMail mail = mail();

        mail.markStale("재발급됨");

        assertThat(mail.getToken()).isNull();
        assertThat(mail.getStatus()).isEqualTo(OutboxMailStatus.FAILED);
    }
}
