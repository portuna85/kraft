package com.kraft.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 빌드가 만든 .gz를 클라이언트가 gzip을 받을 때 그대로 내려주는지. 요청마다 CPU로 압축하지
 * 않고 레벨 9로 미리 압축한 파일을 쓴다 — build.gradle.kts의 processResources와
 * application.yml의 {@code spring.web.resources.chain.compressed}가 함께 있어야 동작한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PrecompressedStaticResourceTest {

    private static final String[] PATHS = {"/css/style.css", "/js/vue-dist/chunks/runtime.js"};

    @Autowired
    private MockMvc mockMvc;

    private static byte[] gunzip(byte[] compressed) throws IOException {
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            return in.readAllBytes();
        }
    }

    @Test
    @DisplayName("gzip을 받는 클라이언트에는 미리 압축한 파일을 보내고, 풀면 원본과 같다")
    void gzipClient_getsPrecompressedFile() throws Exception {
        for (String path : PATHS) {
            MockHttpServletResponse plain = mockMvc.perform(get(path)).andReturn().getResponse();
            MockHttpServletResponse gzipped = mockMvc.perform(get(path).header("Accept-Encoding", "gzip"))
                    .andReturn().getResponse();

            assertThat(gzipped.getStatus()).as(path).isEqualTo(200);
            assertThat(gzipped.getHeader("Content-Encoding")).as(path).isEqualTo("gzip");
            assertThat(gzipped.getHeader("Vary")).as(path).contains("Accept-Encoding");
            assertThat(gzipped.getContentAsByteArray().length).as(path + " 압축본이 더 작다")
                    .isLessThan(plain.getContentAsByteArray().length);
            assertThat(gunzip(gzipped.getContentAsByteArray())).as(path + " 풀면 원본")
                    .isEqualTo(plain.getContentAsByteArray());
        }
    }

    @Test
    @DisplayName("gzip을 받지 않는 클라이언트에는 원본을 그대로 보낸다")
    void clientWithoutGzip_getsOriginal() throws Exception {
        for (String path : PATHS) {
            MockHttpServletResponse plain = mockMvc.perform(get(path)).andReturn().getResponse();

            assertThat(plain.getStatus()).as(path).isEqualTo(200);
            assertThat(plain.getHeader("Content-Encoding")).as(path).isNull();
        }
    }
}
