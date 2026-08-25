package com.kraft.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;

/**
 * B-01: OpenAPI 계약 메타데이터와 노출 범위. 노출 범위는 패키지가 아닌 **경로 기준**으로
 * 잡는다 — com.kraft.ops 패키지에는 공개 API인 InfoController(/api/v1)와 토큰 가드인
 * OpsController(/ops)가 같이 있어 패키지 기준 스캔은 InfoController까지 실수로 빠뜨릴 수
 * 있다. 경로 기준이면 /admin, /actuator, /oauth2, /login, /logout은 자동으로 제외된다.
 *
 * <p>FE-API-02: 그 경로 필터를 두 개의 {@link GroupedOpenApi}로 나눴다 — 공개 계약은
 * /v3/api-docs/public, 운영 콘솔 계약은 /v3/api-docs/ops. 프론트 코드젠
 * (web/scripts/generate-api-types.mjs)이 두 문서를 각각 가져가 별도 파일로 생성하므로
 * 공개 계약 파일이 ops 스키마로 오염되지 않는다. **그룹 빈이 생기면 무그룹
 * /v3/api-docs는 더 이상 문서를 반환하지 않는다** — 저장소 안의 소비자는 위 스크립트와
 * OpenApiContractTest뿐이고 둘 다 그룹 경로를 쓴다.
 *
 * <p>보안 스킴은 스펙에 실리는 2종만 선언한다 — 저장번호가 쓰는 디바이스 토큰 헤더와
 * 커뮤니티 쓰기가 쓰는 세션 쿠키. ops 토큰·admin 세션은 여기서 선언하지 않는다
 * (base OpenAPI의 components는 두 그룹이 공유하므로 공개 계약까지 바뀐다 — 별도 항목).
 */
@Configuration
public class OpenApiConfig {

    private static final String DEVICE_TOKEN_SCHEME = "deviceToken";
    private static final String COMMUNITY_SESSION_SCHEME = "communitySession";

    /** 공개 API 계약 — /v3/api-docs/public. */
    @Bean
    GroupedOpenApi publicApiGroup(OpenApiCustomizer requiredResponsePropertiesCustomizer) {
        return GroupedOpenApi.builder()
                .group("public")
                .pathsToMatch("/api/**")
                .addOpenApiCustomizer(requiredResponsePropertiesCustomizer)
                .build();
    }

    /**
     * 운영 콘솔 계약 — /v3/api-docs/ops. **prod에서는 등록하지 않는다**: /ops는
     * OpsTokenFilter(X-Ops-Token)로 게이트돼 있지만 api-docs 자체는 prod에서도 켜져 있어
     * (application-prod.yml은 swagger-ui만 끈다), 그룹을 그대로 두면 운영 전용 엔드포인트
     * 목록과 DTO 모양을 인증 없이 읽을 수 있다. 코드젠은 local 프로파일에서 돌므로 무영향.
     */
    @Bean
    @Profile("!prod")
    GroupedOpenApi opsApiGroup(OpenApiCustomizer requiredResponsePropertiesCustomizer) {
        return GroupedOpenApi.builder()
                .group("ops")
                .pathsToMatch("/ops/**")
                .addOpenApiCustomizer(requiredResponsePropertiesCustomizer)
                .build();
    }

    @Bean
    OpenAPI kraftOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("KRAFT Lotto Public API")
                        .version("v1")
                        .description("회차·통계·추천·저장번호·커뮤니티·상태 공개 API. "
                                + "/ops는 별도 인증 체계(X-Ops-Token)를 쓰며 ops 그룹 문서로 분리돼 있다"
                                + "(prod에서는 노출하지 않는다). /admin은 어느 계약에도 포함되지 않는다."))
                .components(new Components()
                        .addSecuritySchemes(DEVICE_TOKEN_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Device-Token")
                                .description("저장 번호 API(/api/v1/saved/**)가 요구하는 클라이언트 식별 토큰"))
                        .addSecuritySchemes(COMMUNITY_SESSION_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("JSESSIONID")
                                .description("커뮤니티 쓰기(/api/v1/community/**)가 요구하는 OAuth2 세션. "
                                        + "쓰기 요청은 추가로 XSRF-TOKEN 쿠키를 X-XSRF-TOKEN 헤더로 동봉해야 한다.")));
    }

    /**
     * Java record 응답은 Jackson이 null 필드까지 항상 키로 직렬화하지만, springdoc은
     * record component의 "응답에 존재함"과 "null일 수 있음"을 구분하지 못해 모든 속성을
     * optional로 내보낸다. 실제 API 응답 스키마만 순회해 속성 존재를 required로 고정한다.
     * 요청 DTO는 검증 어노테이션이 required 여부를 계속 결정하므로 이 규칙의 영향을 받지 않는다.
     */
    @Bean
    OpenApiCustomizer requiredResponsePropertiesCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null || openApi.getComponents() == null) {
                return;
            }
            Set<String> visitedReferences = new HashSet<>();
            openApi.getPaths().values().stream()
                    .flatMap(pathItem -> pathItem.readOperations().stream())
                    .filter(operation -> operation.getResponses() != null)
                    .flatMap(operation -> operation.getResponses().values().stream())
                    .filter(response -> response.getContent() != null)
                    .flatMap(response -> response.getContent().values().stream())
                    .map(mediaType -> mediaType.getSchema())
                    .forEach(schema -> requireProperties(
                            schema, openApi.getComponents(), visitedReferences));
        };
    }

    private static void requireProperties(Schema<?> schema, Components components,
                                          Set<String> visitedReferences) {
        if (schema == null) {
            return;
        }
        normalizeNullableReference(schema);
        if (schema.get$ref() != null) {
            String schemaName = schema.get$ref().substring(schema.get$ref().lastIndexOf('/') + 1);
            if (visitedReferences.add(schemaName) && components.getSchemas() != null) {
                requireProperties(components.getSchemas().get(schemaName), components, visitedReferences);
            }
            return;
        }
        if (schema.getProperties() != null && !schema.getProperties().isEmpty()) {
            schema.setRequired(new ArrayList<>(schema.getProperties().keySet()));
            schema.getProperties().values()
                    .forEach(property -> requireProperties(property, components, visitedReferences));
        }
        requireProperties(schema.getItems(), components, visitedReferences);
        requireComposedSchemas(schema.getAllOf(), components, visitedReferences);
        requireComposedSchemas(schema.getAnyOf(), components, visitedReferences);
        requireComposedSchemas(schema.getOneOf(), components, visitedReferences);
    }

    /**
     * swagger-core 2.2.x는 nullable record component가 참조 타입이면
     * {@code {"type":"null","$ref":"..."}}로 만든다. OpenAPI 3.1에서 $ref 형제 키는
     * 허용되지만 일부 생성기는 이를 참조 하나로만 해석해 null을 잃는다. 의미가 같은
     * 명시적 oneOf(ref, null)로 정규화해 생성기 간 계약을 안정화한다.
     */
    private static void normalizeNullableReference(Schema<?> schema) {
        if (schema.get$ref() == null || schema.getTypes() == null
                || !schema.getTypes().contains("null")) {
            return;
        }
        String reference = schema.get$ref();
        schema.set$ref(null);
        schema.setTypes(null);
        schema.setOneOf(List.of(
                new Schema<>().$ref(reference),
                new Schema<>().types(Set.of("null"))));
    }

    private static void requireComposedSchemas(List<Schema> schemas, Components components,
                                               Set<String> visitedReferences) {
        if (schemas != null) {
            schemas.forEach(schema -> requireProperties(schema, components, visitedReferences));
        }
    }
}
