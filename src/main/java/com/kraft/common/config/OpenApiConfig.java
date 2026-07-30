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
import org.springdoc.core.customizers.OpenApiCustomizer;

/**
 * B-01: 공개 API 계약(/v3/api-docs) 메타데이터. pathsToMatch(application.yml)가 이미
 * 노출 범위를 /api/**로 제한하므로, 여기서는 스펙에 실리는 보안 스킴 2종만 선언한다
 * — 저장번호가 쓰는 디바이스 토큰 헤더와 커뮤니티 쓰기가 쓰는 세션 쿠키. ops 토큰·admin
 * 세션은 /api/**에 없으므로 이 스펙 범위 밖이다.
 */
@Configuration
public class OpenApiConfig {

    private static final String DEVICE_TOKEN_SCHEME = "deviceToken";
    private static final String COMMUNITY_SESSION_SCHEME = "communitySession";

    @Bean
    OpenAPI kraftOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("KRAFT Lotto Public API")
                        .version("v1")
                        .description("회차·통계·추천·저장번호·커뮤니티·상태 공개 API. "
                                + "/ops, /admin은 별도 인증 체계를 쓰며 이 계약에 포함되지 않는다."))
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
