package com.kraft.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * FE-API-02 회귀 가드: ops 그룹 문서(/v3/api-docs/ops)가 prod에서 등록되지 않는지 고정한다.
 * api-docs 자체는 prod에서도 켜져 있으므로(application-prod.yml은 swagger-ui만 끈다),
 * @Profile("!prod")이 사라지면 토큰 게이트된 운영 엔드포인트의 경로·DTO 모양이 인증 없이
 * 읽힌다. 전체 애플리케이션을 띄우지 않고 이 설정 클래스만 프로파일별로 평가한다.
 */
@DisplayName("ops OpenAPI 그룹의 프로파일 경계")
class OpenApiOpsGroupProfileTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(OpenApiConfig.class);

    @Test
    @DisplayName("prod가 아니면 public·ops 두 그룹이 모두 등록된다")
    void nonProdRegistersBothGroups() {
        contextRunner.withPropertyValues("spring.profiles.active=local")
                .run(context -> assertThat(context.getBeansOfType(GroupedOpenApi.class).values())
                        .extracting(GroupedOpenApi::getGroup)
                        .containsExactlyInAnyOrder("public", "ops"));
    }

    @Test
    @DisplayName("prod에서는 ops 그룹이 등록되지 않는다")
    void prodOmitsOpsGroup() {
        contextRunner.withPropertyValues("spring.profiles.active=prod")
                .run(context -> assertThat(context.getBeansOfType(GroupedOpenApi.class).values())
                        .extracting(GroupedOpenApi::getGroup)
                        .containsExactly("public"));
    }
}
