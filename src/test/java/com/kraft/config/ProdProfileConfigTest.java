package com.kraft.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * application-prod.yml의 프로퍼티가 실제 Spring Boot 속성 경로에 바인딩되는지 확인한다.
 * 세션 쿠키 secure는 {@code spring.servlet}이 아니라
 * {@code server.servlet} 아래여야 {@code ServerProperties}에 바인딩된다 — 잘못된 경로에
 * 두면 YAML 파싱은 통과하지만 어떤 프로퍼티에도 매핑되지 않고 조용히 무시된다(컨텍스트
 * 기동도, 다른 테스트도 실패하지 않는다는 점이 위험하다).
 */
class ProdProfileConfigTest {

    @Test
    @DisplayName("server.servlet.session.cookie.secure가 true로 바인딩된다")
    void sessionCookieSecure_isBoundUnderServerServlet() throws Exception {
        String classpathYaml = "application-prod.yml";
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> sources = loader.load(classpathYaml, new ClassPathResource(classpathYaml));
        assertThat(sources).isNotEmpty();

        Binder binder = new Binder(ConfigurationPropertySources.from(sources));

        Boolean secure = binder.bind("server.servlet.session.cookie.secure", Boolean.class)
                .orElseThrow(() -> new AssertionError(
                        "server.servlet.session.cookie.secure가 바인딩되지 않았다"));

        assertThat(secure).isTrue();
    }

    /**
     * {@code /readyz}의 루프백 판정({@code HealthController})은 {@code request.getRemoteAddr()}가 프록시
     * 뒤에서 실제 클라이언트 IP를 반영한다는 데 기대고 있다 — 그러려면 forward-headers 처리가 켜져
     * 있어야 한다. 이 설정이 조용히 사라지면 프록시(로컬 nginx)의 주소인 127.0.0.1이 항상 보여
     * 외부에서도 /readyz가 열린다(P2-3).
     */
    @Test
    @DisplayName("P2-3: server.forward-headers-strategy가 native로 설정되어 /readyz 루프백 판정이 유지된다")
    void forwardHeadersStrategy_isNative() throws Exception {
        String classpathYaml = "application.yml";
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load(classpathYaml, new ClassPathResource(classpathYaml));

        String strategy = new Binder(ConfigurationPropertySources.from(sources))
                .bind("server.forward-headers-strategy", String.class)
                .orElseThrow(() -> new AssertionError("server.forward-headers-strategy가 설정되지 않았다"));

        assertThat(strategy).isEqualTo("native");
    }
}
