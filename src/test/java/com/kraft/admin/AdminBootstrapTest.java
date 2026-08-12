package com.kraft.admin;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("관리자 계정 부트스트랩 테스트")
class AdminBootstrapTest {

    @Mock
    private AdminUserRepository adminUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-01T00:00:00Z"), ZoneOffset.UTC);

    private ListAppender<ILoggingEvent> captureLogs() {
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(AdminBootstrap.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    @Test
    @DisplayName("부트스트랩 값이 있고 admin_users가 비어 있으면 계정을 만든다")
    void bootstrapValuesPresent_emptyAdminUsers_createsAccount() {
        given(adminUserRepository.count()).willReturn(0L);
        given(passwordEncoder.encode(anyString())).willReturn("encoded");
        AdminBootstrap bootstrap = new AdminBootstrap(adminUserRepository, passwordEncoder, CLOCK,
                new MockEnvironment(), "admin", "s3cret-password");

        bootstrap.run();

        verify(adminUserRepository).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("부트스트랩 값이 있어도 admin_users가 이미 있으면 만들지 않는다")
    void bootstrapValuesPresent_nonEmptyAdminUsers_skipsCreation() {
        given(adminUserRepository.count()).willReturn(1L);
        AdminBootstrap bootstrap = new AdminBootstrap(adminUserRepository, passwordEncoder, CLOCK,
                new MockEnvironment(), "admin", "s3cret-password");

        bootstrap.run();

        verify(adminUserRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("M-12: prod 프로파일에서 부트스트랩 값도 없고 admin_users도 비어 있으면 경고 로그를 남긴다")
    void prodProfile_noBootstrapValues_emptyAdminUsers_logsWarning() {
        given(adminUserRepository.count()).willReturn(0L);
        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("prod");
        AdminBootstrap bootstrap = new AdminBootstrap(adminUserRepository, passwordEncoder, CLOCK,
                prodEnv, "", "");

        ListAppender<ILoggingEvent> appender = captureLogs();
        bootstrap.run();

        assertThat(appender.list)
                .anyMatch(event -> event.getLevel() == Level.WARN
                        && event.getFormattedMessage().contains("admin_users가 비어"));
        verify(adminUserRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("prod 프로파일이어도 admin_users가 이미 있으면 경고하지 않는다")
    void prodProfile_noBootstrapValues_nonEmptyAdminUsers_doesNotWarn() {
        given(adminUserRepository.count()).willReturn(1L);
        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("prod");
        AdminBootstrap bootstrap = new AdminBootstrap(adminUserRepository, passwordEncoder, CLOCK,
                prodEnv, "", "");

        ListAppender<ILoggingEvent> appender = captureLogs();
        bootstrap.run();

        assertThat(appender.list).noneMatch(event -> event.getLevel() == Level.WARN);
    }

    @Test
    @DisplayName("prod가 아닌 프로파일에서는 부트스트랩 값·admin_users가 없어도 경고하지 않는다")
    void nonProdProfile_noBootstrapValues_emptyAdminUsers_doesNotWarn() {
        // isProdProfile()이 false라 &&의 뒤쪽(adminUserRepository.count())은 아예 호출되지
        // 않는다 — 여기서 count()를 스텁하면 Mockito strict 모드가 "쓰이지 않는 스텁"으로 막는다.
        MockEnvironment devEnv = new MockEnvironment();
        devEnv.setActiveProfiles("local");
        AdminBootstrap bootstrap = new AdminBootstrap(adminUserRepository, passwordEncoder, CLOCK,
                devEnv, "", "");

        ListAppender<ILoggingEvent> appender = captureLogs();
        bootstrap.run();

        assertThat(appender.list).noneMatch(event -> event.getLevel() == Level.WARN);
    }
}
