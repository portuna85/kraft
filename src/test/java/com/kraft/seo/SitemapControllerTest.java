package com.kraft.seo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 하위 sitemap 경로의 응답 규칙. 컨텍스트를 띄우지 않아 {@link SitemapService}의 10분 캐시가 다른 테스트와 섞이지 않는다. */
class SitemapControllerTest {

    private final SitemapService sitemapService = mock(SitemapService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SitemapController(sitemapService, "https://kraft.example")).build();
    }

    @Test
    @DisplayName("index가 가리키는 하위 sitemap은 XML로 내려준다")
    void knownChunk_isServedAsXml() throws Exception {
        when(sitemapService.postsChunkXml(3L)).thenReturn(Optional.of("<urlset/>"));

        mockMvc.perform(get("/sitemap-posts-3.xml"))
                .andExpect(status().isOk())
                .andExpect(content().xml("<urlset/>"));
    }

    @Test
    @DisplayName("모르는 경계이거나 숫자가 아닌 값이면 404·400이다")
    void unknownOrMalformedChunk_isRejected() throws Exception {
        when(sitemapService.postsChunkXml(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/sitemap-posts-999.xml")).andExpect(status().isNotFound());
        mockMvc.perform(get("/sitemap-posts-abc.xml")).andExpect(status().isBadRequest());
    }
}
