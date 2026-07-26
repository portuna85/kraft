package com.kraft.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
}
