package com.kraft.common.web;

import com.kraft.Application;
import com.kraft.winningnumber.WinningNumberRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("공개 캐시 제어 필터 테스트")
class PublicApiCacheControlFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WinningNumberRepository winningNumberRepository;

    @BeforeEach
    void seedRound1() {
        winningNumberRepository.deleteAll();
        winningNumberRepository.save(com.kraft.winningnumber.WinningNumberTestFactory.create(
                1,
                LocalDate.of(2002, 12, 7),
                10, 23, 29, 33, 37, 40,
                16,
                857_956_000L,
                0L, 0, 0L, 0L,
                OffsetDateTime.now(ZoneId.of("Asia/Seoul"))
        ));
    }

    @Test
    @DisplayName("조건부 요청 이태그가 일치하면 304를 빈 본문으로 반환한다")
    void matchingIfNoneMatch_returnsNotModifiedWithEmptyBody() throws Exception {
        MvcResult first = mockMvc.perform(get("/api/v1/rounds/latest"))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(header().string("Cache-Control", "public, max-age=60, must-revalidate"))
                .andReturn();

        String etag = first.getResponse().getHeader("ETag");

        mockMvc.perform(get("/api/v1/rounds/latest").header("If-None-Match", etag))
                .andExpect(status().isNotModified())
                .andExpect(header().string("ETag", etag))
                .andExpect(header().string("Cache-Control", "public, max-age=60, must-revalidate"))
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isEmpty());
    }

    @Test
    @DisplayName("장애 이력 응답의 이태그는 회차 기반이 아니라 내용 해시다")
    void incidentsPath_getsMd5EtagNotRoundEtag() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/status/incidents"))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andReturn();

        String etag = result.getResponse().getHeader("ETag");

        assertThat(etag).doesNotContain("round-");
    }

    @Test
    @DisplayName("L-03: 약한 이태그(W/)도 304를 받는다")
    void weakEtag_returnsNotModified() throws Exception {
        MvcResult first = mockMvc.perform(get("/api/v1/rounds/latest"))
                .andExpect(status().isOk())
                .andReturn();
        String etag = first.getResponse().getHeader("ETag");

        mockMvc.perform(get("/api/v1/rounds/latest").header("If-None-Match", "W/" + etag))
                .andExpect(status().isNotModified());
    }

    @Test
    @DisplayName("L-03: 쉼표로 나열된 여러 검증자 중 하나만 일치해도 304를 받는다")
    void multipleCandidates_matchesAnyOne_returnsNotModified() throws Exception {
        MvcResult first = mockMvc.perform(get("/api/v1/rounds/latest"))
                .andExpect(status().isOk())
                .andReturn();
        String etag = first.getResponse().getHeader("ETag");

        mockMvc.perform(get("/api/v1/rounds/latest")
                        .header("If-None-Match", "\"불일치\", " + etag + ", \"다른불일치\""))
                .andExpect(status().isNotModified());
    }

    @Test
    @DisplayName("L-03: 와일드카드(*)는 항상 304를 받는다")
    void wildcard_alwaysReturnsNotModified() throws Exception {
        mockMvc.perform(get("/api/v1/rounds/latest"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/rounds/latest").header("If-None-Match", "*"))
                .andExpect(status().isNotModified());
    }

    @Test
    @DisplayName("L-03: 불일치하는 이태그는 200을 그대로 받는다")
    void nonMatchingEtag_returnsOk() throws Exception {
        mockMvc.perform(get("/api/v1/rounds/latest").header("If-None-Match", "\"불일치\""))
                .andExpect(status().isOk());
    }
}
