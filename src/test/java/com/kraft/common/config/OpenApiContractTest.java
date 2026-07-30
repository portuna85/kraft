package com.kraft.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kraft.Application;
import java.util.Iterator;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * B-01 회귀 가드: /v3/api-docs가 /api/**만 담고, /ops·/admin·/actuator를 실수로
 * 노출하지 않는지 고정한다. pathsToMatch(application.yml)가 바뀌거나 신규 컨트롤러가
 * 잘못된 base path로 추가돼도 이 테스트가 먼저 실패한다.
 */
@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("OpenAPI 계약 노출 범위 회귀 가드")
class OpenApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    @DisplayName("/v3/api-docs는 200을 반환하고 /api/**로 시작하는 경로만 포함한다")
    void apiDocs_onlyExposesPublicApiPaths() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode paths = OBJECT_MAPPER.readTree(body).path("paths");
        assertThat(paths.isObject()).isTrue();
        assertThat(paths.size()).isPositive();

        Iterator<String> pathNames = paths.fieldNames();
        while (pathNames.hasNext()) {
            String path = pathNames.next();
            assertThat(path).as("노출된 경로: %s", path).startsWith("/api/");
            assertThat(path).as("ops 경로가 계약에 섞였다: %s", path).doesNotStartWith("/ops");
            assertThat(path).as("admin 경로가 계약에 섞였다: %s", path).doesNotStartWith("/admin");
        }
    }

    @Test
    @DisplayName("응답 record 필드는 항상 required이며 실제 nullable 필드만 null을 허용한다")
    void apiDocs_responseRecordsDistinguishPresenceFromNullability() throws Exception {
        JsonNode schemas = apiDocs().path("components").path("schemas");

        assertRequiredExactly(schemas.path("CommunitySessionResponse"),
                "loggedIn", "userId", "nickname", "activeProviders");
        assertThat(schemas.path("CommunitySessionResponse").path("properties").path("userId")
                .path("type")).anySatisfy(type -> assertThat(type.asText()).isEqualTo("null"));
        assertThat(schemas.path("CommunitySessionResponse").path("properties").path("nickname")
                .path("type")).anySatisfy(type -> assertThat(type.asText()).isEqualTo("null"));

        assertRequiredExactly(schemas.path("SavedNumberResponse"),
                "id", "numbers", "label", "source", "createdAt");
        assertThat(schemas.path("SavedNumberResponse").path("properties").path("label")
                .path("type")).anySatisfy(type -> assertThat(type.asText()).isEqualTo("null"));

        JsonNode attachment = schemas.path("CommunityPostResponse").path("properties")
                .path("recommendationAttachment");
        assertThat(attachment.path("oneOf")).hasSize(2);
        assertThat(attachment.path("oneOf").toString())
                .contains("#/components/schemas/RecommendationAttachmentView")
                .contains("\"null\"");
    }

    private JsonNode apiDocs() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return OBJECT_MAPPER.readTree(body);
    }

    private static void assertRequiredExactly(JsonNode schema, String... names) {
        assertThat(schema.path("required"))
                .extracting(JsonNode::asText)
                .containsExactlyInAnyOrderElementsOf(Set.of(names));
    }
}
