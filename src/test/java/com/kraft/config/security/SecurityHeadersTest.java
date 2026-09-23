package com.kraft.config.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link SecurityConfig}가 붙이는 CSP·Referrer-Policy·HSTS를 확인한다(개선 보고서 SEC-05).
 * <p>
 * 이 앱은 전 화면이 자체 호스팅 CSS·JS만 쓴다 — 외부 CDN·폰트도, 인라인 스크립트·스타일도
 * 없다(F08 이후로 jQuery·Bootstrap도 직접 서빙한다. 마운트 실패 안내도 이 작업에서 인라인
 * {@code <script>}에서 {@code /js/mount-failure.js}로 뺐다). 그래서 {@code default-src 'self'}
 * 하나로 거의 모든 지시어를 막을 수 있다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityHeadersTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("SEC-05: 응답에 CSP가 self 기준으로 붙는다")
    void response_hasContentSecurityPolicy() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")))
                .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")))
                .andExpect(header().string("Content-Security-Policy", containsString("style-src 'self'")))
                .andExpect(header().string("Content-Security-Policy", containsString("img-src 'self' data: blob:")))
                .andExpect(header().string("Content-Security-Policy", containsString("object-src 'none'")));
    }

    @Test
    @DisplayName("SEC-05: 응답에 Referrer-Policy가 strict-origin-when-cross-origin으로 붙는다")
    void response_hasReferrerPolicy() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"));
    }

    /**
     * TLS는 리버스 프록시가 종단하고 {@code server.forward-headers-strategy}는 아직 설정하지
     * 않았다 — {@code request.isSecure()}가 항상 false일 수 있는 환경에서도 HSTS가 붙어야
     * 한다(기본 매처를 그대로 썼다면 이 테스트가 실패해야 정상이다: MockMvc 요청은 HTTPS가
     * 아니다).
     */
    @Test
    @DisplayName("SEC-05: HTTPS가 아닌 요청에도 HSTS가 붙는다(프록시 뒤에서도 항상 적용)")
    void response_hasHstsEvenOverPlainHttp() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(header().string("Strict-Transport-Security", containsString("max-age=31536000")))
                .andExpect(header().string("Strict-Transport-Security", containsString("includeSubDomains")));
    }
}
