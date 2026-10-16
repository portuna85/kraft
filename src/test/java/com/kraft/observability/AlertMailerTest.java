package com.kraft.observability;

import com.kraft.user.mail.EmailSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.anyString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * HealthReporter가 상태 이상을 발견했을 때 AlertMailer가 실제로 메일을 보내는 조건만 본다.
 * 발송 성공 여부(EmailSender 내부)는 이 클래스의 관심사가 아니다.
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

    /** 테스트가 직접 시각을 옮기는 시계. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-16T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    @DisplayName("발송이 실패하면 쿨다운을 기다리지 않고 짧은 대기 뒤 다음 점검에서 다시 시도하고, 성공하면 그때부터 쿨다운을 적용한다")
    void retriesSoonAfterFailureThenAppliesCooldownAfterSuccess() {
        EmailSender sender = mock(EmailSender.class);
        MutableClock clock = new MutableClock();
        AlertMailer alertMailer = new AlertMailer(sender, "admin@example.com", Duration.ofHours(1), clock);
        doThrow(new RuntimeException("SMTP 오류")).when(sender).send(anyString(), anyString(), anyString());

        alertMailer.alertIfDue(breach(), LIMITS);
        verify(sender, times(1)).send(anyString(), anyString(), anyString());

        // 실패 직후 같은 시각에는 아직 쉬는 중이다.
        alertMailer.alertIfDue(breach(), LIMITS);
        verify(sender, times(1)).send(anyString(), anyString(), anyString());

        // 다음 점검(5분 뒤)에는 한 시간을 기다리지 않고 다시 시도하고, 이번에는 성공한다.
        clock.advance(Duration.ofMinutes(5));
        doNothing().when(sender).send(anyString(), anyString(), anyString());
        alertMailer.alertIfDue(breach(), LIMITS);
        verify(sender, times(2)).send(anyString(), anyString(), anyString());

        // 성공한 뒤에는 정상 쿨다운이 적용된다.
        clock.advance(Duration.ofMinutes(59));
        alertMailer.alertIfDue(breach(), LIMITS);
        verify(sender, times(2)).send(anyString(), anyString(), anyString());

        clock.advance(Duration.ofMinutes(1));
        alertMailer.alertIfDue(breach(), LIMITS);
        verify(sender, times(3)).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("실패 뒤 재시도 대기는 쿨다운보다 길어지지 않는다")
    void failureRetryNeverExceedsCooldown() {
        EmailSender sender = mock(EmailSender.class);
        MutableClock clock = new MutableClock();
        Duration shortCooldown = Duration.ofSeconds(10);
        AlertMailer alertMailer = new AlertMailer(sender, "admin@example.com", shortCooldown, clock);
        doThrow(new RuntimeException("SMTP 오류")).when(sender).send(anyString(), anyString(), anyString());

        alertMailer.alertIfDue(breach(), LIMITS);
        clock.advance(shortCooldown);
        alertMailer.alertIfDue(breach(), LIMITS);

        verify(sender, times(2)).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("한 종류의 발송 실패는 쿨다운 중인 다른 종류의 대기에 영향을 주지 않는다")
    void failureOfOneKindDoesNotAffectOthersCooldown() {
        EmailSender sender = mock(EmailSender.class);
        MutableClock clock = new MutableClock();
        AlertMailer alertMailer = new AlertMailer(sender, "admin@example.com", Duration.ofHours(1), clock);

        // 오류율 종류는 성공해 쿨다운에 들어간다.
        alertMailer.alertIfDue(breach(), LIMITS);
        verify(sender, times(1)).send(anyString(), anyString(), anyString());

        // 다른 종류(느린 요청 비율)가 새로 넘으면 보내려다 실패하고, 이미 쿨다운인 오류율은 그대로 유지된다.
        HealthSnapshot another = new HealthSnapshot(100, 30, 0, 5000, 9000, 2, 10, 0, 50_000_000_000L, 0, 0, 0, 0, 0, 0, true);
        clock.advance(Duration.ofMinutes(5));
        doThrow(new RuntimeException("SMTP 오류")).when(sender).send(anyString(), anyString(), anyString());
        alertMailer.alertIfDue(another, LIMITS);
        verify(sender, times(2)).send(anyString(), anyString(), anyString());

        // 실패한 종류가 짧은 대기 뒤 다시 시도되는 동안에도, 이미 알린 오류율은 쿨다운(1시간) 안이라 단독으로는 다시 보내지 않는다.
        doNothing().when(sender).send(anyString(), anyString(), anyString());
        clock.advance(Duration.ofMinutes(1));
        alertMailer.alertIfDue(breach(), LIMITS);
        verify(sender, times(2)).send(anyString(), anyString(), anyString());

        alertMailer.alertIfDue(another, LIMITS);
        verify(sender, times(3)).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("발송 중에 겹쳐 들어온 호출은 같은 알림을 또 보내지 않는다")
    void concurrentCallWhileSendingDoesNotSendTwice() throws Exception {
        EmailSender sender = mock(EmailSender.class);
        CountDownLatch inSend = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        org.mockito.Mockito.doAnswer(invocation -> {
            calls.incrementAndGet();
            inSend.countDown();
            release.await(5, TimeUnit.SECONDS);
            return null;
        }).when(sender).send(anyString(), anyString(), anyString());
        AlertMailer alertMailer = new AlertMailer(sender, "admin@example.com", Duration.ZERO);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            var first = executor.submit(() -> alertMailer.alertIfDue(breach(), LIMITS));
            assertThat(inSend.await(5, TimeUnit.SECONDS)).isTrue();

            alertMailer.alertIfDue(breach(), LIMITS);

            release.countDown();
            first.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        assertThat(calls.get()).isEqualTo(1);
    }
}
