package com.kraft.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 세션 타임아웃(2시간)이 실제로 적용되는지 확인한다(BE-01).
 * <p>
 * 예전에는 {@code spring.servlet.session.timeout}에 두어 어떤 프로퍼티에도 바인딩되지 않았고,
 * 실제 만료는 기본값 30분이었다. {@code ProdProfileConfigTest}(SEC-01)와 같은 유형의 실수라
 * 설정 경로와 실제 세션 값을 모두 본다.
 */
// MOCK 환경에서는 Boot가 WAR 배포용 SessionTimeout(컨테이너 값)을 골라 null이 된다. 운영과 같은
// 임베디드 서버 경로(server.servlet.session.timeout)를 타려면 실제 포트로 띄워야 한다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SessionTimeoutConfigTest {

    private static final Duration EXPECTED = Duration.ofHours(2);

    @Autowired
    private SessionRepository<? extends Session> sessionRepository;

    @Test
    @DisplayName("BE-01: 타임아웃은 server.servlet.session.timeout에만 있고 spring.servlet 아래에는 없다")
    void timeout_isBoundUnderServerServlet() throws Exception {
        String classpathYaml = "application.yml";
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load(classpathYaml, new ClassPathResource(classpathYaml));
        Binder binder = new Binder(ConfigurationPropertySources.from(sources));

        Duration timeout = binder.bind("server.servlet.session.timeout", Duration.class)
                .orElseThrow(() -> new AssertionError("server.servlet.session.timeout이 바인딩되지 않았다"));

        assertThat(timeout).isEqualTo(EXPECTED);
        assertThat(binder.bind("spring.servlet.session.timeout", Duration.class).isBound())
                .as("spring.servlet.session.timeout은 존재하지 않는 프로퍼티라 무시된다")
                .isFalse();
    }

    @Test
    @DisplayName("BE-01: 새로 만든 세션의 maxInactiveInterval이 2시간이다")
    void newSession_hasTwoHourInactiveInterval() {
        Session session = sessionRepository.createSession();

        assertThat(session.getMaxInactiveInterval()).isEqualTo(EXPECTED);
    }
}
