package com.kraft.common.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.kraft.winningnumber.RoundEtagProvider;
import com.kraft.winningnumber.WinningNumbersCollectedEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Method;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("비동기 리스너 미처리 예외 관측")
class AsyncFailureMonitorTest {

    private final ch.qos.logback.classic.Logger logger =
            (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(AsyncFailureMonitor.class);
    private ListAppender<ILoggingEvent> appender;

    @AfterEach
    void detachAppender() {
        if (appender != null) {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    @DisplayName("알려진 리스너 실패를 제한된 태그로 세고 원인 예외를 ERROR 로그에 남긴다")
    void knownListenerFailure_recordsBoundedMetricAndThrowable() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AsyncFailureMonitor monitor = new AsyncFailureMonitor(registry);
        Method method = RoundEtagProvider.class.getDeclaredMethod(
                "onCollected", WinningNumbersCollectedEvent.class);
        IllegalStateException failure = new IllegalStateException("database unavailable");
        ListAppender<ILoggingEvent> appender = captureLogs();

        monitor.handleUncaughtException(failure, method, new WinningNumbersCollectedEvent(1201, true));

        assertThat(registry.get("kraft_async_listener_failures_total")
                .tags("listener", "round-etag", "outcome", "failure")
                .counter().count()).isEqualTo(1.0);
        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage()).contains("round-etag", "onCollected");
            assertThat(event.getThrowableProxy()).isNotNull();
        });
    }

    @Test
    @DisplayName("미등록 비동기 메서드는 unknown으로 묶고 인자 값은 로그에 노출하지 않는다")
    void unknownListenerFailure_usesFallbackTagWithoutParameters() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AsyncFailureMonitor monitor = new AsyncFailureMonitor(registry);
        Method method = UnknownAsyncTarget.class.getDeclaredMethod("run", String.class);
        ListAppender<ILoggingEvent> appender = captureLogs();

        monitor.handleUncaughtException(new RuntimeException("failed"), method, "top-secret-token");

        assertThat(registry.get("kraft_async_listener_failures_total")
                .tags("listener", "unknown", "outcome", "failure")
                .counter().count()).isEqualTo(1.0);
        assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                .allSatisfy(message -> assertThat(message).doesNotContain("top-secret-token"));
    }

    private ListAppender<ILoggingEvent> captureLogs() {
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private static final class UnknownAsyncTarget {
        @SuppressWarnings("unused")
        void run(String value) {
            // 테스트에서 리플렉션으로 메서드 메타데이터만 사용한다.
        }
    }
}
