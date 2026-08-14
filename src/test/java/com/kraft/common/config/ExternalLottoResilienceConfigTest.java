package com.kraft.common.config;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("외부 로또 수집 재시도 계약")
class ExternalLottoResilienceConfigTest {

    @Test
    @DisplayName("재시도는 2회·500ms이며 I/O와 upstream 5xx에만 적용한다")
    void retryEnvelope_isFiniteAndLimitedToTransientFailures() throws IOException {
        List<PropertySource<?>> documents = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"));
        String prefix = "resilience4j.retry.instances.externalLotto.";

        assertThat(value(documents, prefix + "max-attempts")).isEqualTo(2);
        assertThat(value(documents, prefix + "wait-duration")).isEqualTo("500ms");
        assertThat(value(documents, prefix + "retry-exceptions[0]"))
                .isEqualTo("org.springframework.web.client.ResourceAccessException");
        assertThat(value(documents, prefix + "retry-exceptions[1]"))
                .isEqualTo("org.springframework.web.client.HttpServerErrorException");
        assertThat(value(documents, prefix + "retry-exceptions[2]")).isNull();
    }

    private static Object value(List<PropertySource<?>> documents, String propertyName) {
        return documents.stream()
                .map(document -> document.getProperty(propertyName))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }
}
