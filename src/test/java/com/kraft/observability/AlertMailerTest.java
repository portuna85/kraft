package com.kraft.observability;

import com.kraft.user.mail.EmailSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * HealthReporter가 상태 이상을 발견했을 때 AlertMailer가 실제로 메일을 보내는 조건만 본다
 * (A-OPS-02) — 발송 성공 여부(EmailSender 내부)는 이 클래스의 관심사가 아니다.
 */
class AlertMailerTest {

    private static final HealthThresholds LIMITS = new HealthThresholds(
            20, 0.1, 0, 1000, 0.8, 1_073_741_824L, 20, 0, 5, 0, 200, 200);

    private static HealthSnapshot breach() {
        // 오류율만 넘긴 스냅숏.
        return new HealthSnapshot(100, 30, 0, 120, 400, 2, 10, 0, 50_000_000_000L, 0, 0, 0, 0, 0, 0, true);
    }

    private static HealthSnapshot healthy() {
        return new HealthSnapshot(100, 2, 0, 120, 400, 2, 10, 0, 50_000_000_000L, 0, 0, 0, 0, 0, 0, true);
    }

    @Test
    @DisplayName("관리자 주소를 설정하지 않았으면 기준을 넘겨도 보내지 않는다")
    void doesNothingWhenAlertEmailIsBlank() {
        EmailSender sender = mock(EmailSender.class);
        AlertMailer alertMailer = new AlertMailer(sender, "", Duration.ofHours(1));

        alertMailer.alertIfDue(breach(), LIMITS);

        verify(sender, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("disabled()는 관리자 주소가 없는 것과 같다")
    void disabledFactoryNeverSends() {
        AlertMailer.disabled().alertIfDue(breach(), LIMITS);
        // EmailSender가 null이라 send를 호출했다면 NPE로 이 테스트 자체가 실패한다.
    }

    @Test
    @DisplayName("정상 상태에서는 관리자 주소가 있어도 보내지 않는다")
    void doesNothingWhenHealthy() {
        EmailSender sender = mock(EmailSender.class);
        AlertMailer alertMailer = new AlertMailer(sender, "admin@example.com", Duration.ofHours(1));

        alertMailer.alertIfDue(healthy(), LIMITS);

        verify(sender, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("기준을 넘기면 관리자에게 상태 이상 메일을 보낸다")
    void sendsAlertWhenBreached() {
        EmailSender sender = mock(EmailSender.class);
        AlertMailer alertMailer = new AlertMailer(sender, "admin@example.com", Duration.ofHours(1));

        alertMailer.alertIfDue(breach(), LIMITS);

        verify(sender).send(eq("admin@example.com"), anyString(), anyString());
    }

    @Test
    @DisplayName("같은 종류의 문제는 쿨다운 안에서 다시 보내지 않는다")
    void suppressesRepeatedAlertOfSameKindWithinCooldown() {
        EmailSender sender = mock(EmailSender.class);
        AlertMailer alertMailer = new AlertMailer(sender, "admin@example.com", Duration.ofHours(1));

        alertMailer.alertIfDue(breach(), LIMITS);
        alertMailer.alertIfDue(breach(), LIMITS);
        alertMailer.alertIfDue(breach(), LIMITS);

        verify(sender, times(1)).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("쿨다운이 0이면 매번 다시 보낸다")
    void sendsAgainImmediatelyWhenCooldownIsZero() {
        EmailSender sender = mock(EmailSender.class);
        AlertMailer alertMailer = new AlertMailer(sender, "admin@example.com", Duration.ZERO);

        alertMailer.alertIfDue(breach(), LIMITS);
        alertMailer.alertIfDue(breach(), LIMITS);

        verify(sender, times(2)).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("메일 발송이 실패해도 예외를 던지지 않는다 — 상태 점검 자체를 막으면 안 된다")
    void sendFailureDoesNotPropagate() {
        EmailSender sender = mock(EmailSender.class);
        willThrow(new RuntimeException("SMTP 오류")).given(sender).send(anyString(), anyString(), anyString());
        AlertMailer alertMailer = new AlertMailer(sender, "admin@example.com", Duration.ofHours(1));

        alertMailer.alertIfDue(breach(), LIMITS);
    }
}
