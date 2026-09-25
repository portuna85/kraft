package com.kraft.observability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 템플릿이 실제로 내보내는 버전 JS 주소가 요청 지표에서 빠지는지, 실제 리소스 체인·필터
 * 등록으로 확인한다(평가 보고서 2026-09-25 F12). 버전 문자열을 테스트에 적지 않고 화면에서
 * 읽어 온다 — 설정과 필터가 서로 다른 값을 보면 여기서 드러난다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class VersionedStaticMetricsIntegrationTest {

    private static final Pattern MAIN_JS = Pattern.compile("src=\"(/[^\"/]+/js/app/main\\.js)\"");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RequestMetrics metrics;

    @Test
    @DisplayName("화면이 참조하는 /{버전}/js/... 요청은 세지 않고, 화면 요청은 센다")
    void versionedJsFromTemplate_isNotCounted() throws Exception {
        String html = mockMvc.perform(get("/")).andReturn().getResponse().getContentAsString();
        Matcher matcher = MAIN_JS.matcher(html);
        assertThat(matcher.find()).as("footer가 버전이 붙은 main.js를 참조해야 한다").isTrue();
        String versionedJs = matcher.group(1);
        assertThat(versionedJs).doesNotStartWith("/js/");

        metrics.drain();
        mockMvc.perform(get(versionedJs)).andExpect(status().isOk());
        assertThat(metrics.drain().requests()).as("버전 JS 요청").isZero();

        mockMvc.perform(get("/recommend"));
        assertThat(metrics.drain().requests()).as("화면 요청").isEqualTo(1);
    }
}
