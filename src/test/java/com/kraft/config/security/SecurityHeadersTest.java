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
 * {@link SecurityConfig}가 붙이는 CSP·Referrer-Policy·HSTS를 확인한다.
 * <p>
 * 이 앱은 전 화면이 자체 호스팅 CSS·JS만 쓴다 — 외부 CDN·폰트도, 인라인 스크립트·스타일도 없다(마운트 실패 안내도 인라인 {@code <script>}가 아니라 {@code /js/mount-failure.js}다). 그래서 {@code default-src 'self'} 하나로 거의 모든 지시어를 막을 수 있다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityHeadersTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("응답에 CSP가 self 기준으로 붙는다")
    void response_hasContentSecurityPolicy() throws Exception {
        mockMvc.perform(get("/recommend"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")))
                .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")))
                .andExpect(header().string("Content-Security-Policy", containsString("style-src 'self'")))
                .andExpect(header().string("Content-Security-Policy", containsString("img-src 'self' data: blob:")))
                .andExpect(header().string("Content-Security-Policy", containsString("object-src 'none'")))
                .andExpect(header().string("Content-Security-Policy", containsString("upgrade-insecure-requests")));
    }

    @Test
    @DisplayName("쓰지 않는 브라우저 기능을 Permissions-Policy로 막는다")
    void response_hasPermissionsPolicy() throws Exception {
        mockMvc.perform(get("/recommend"))
                .andExpect(status().isOk())
                .andExpect(header().string("Permissions-Policy", containsString("camera=()")))
                .andExpect(header().string("Permissions-Policy", containsString("microphone=()")))
                .andExpect(header().string("Permissions-Policy", containsString("geolocation=()")))
                .andExpect(header().string("Permissions-Policy", containsString("payment=()")));
    }

    @Test
    @DisplayName("다른 오리진 탭과 window 참조를 격리한다")
    void response_hasCrossOriginOpenerPolicy() throws Exception {
        mockMvc.perform(get("/recommend"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cross-Origin-Opener-Policy", "same-origin"));
    }

    @Test
    @DisplayName("응답에 Referrer-Policy가 strict-origin-when-cross-origin으로 붙는다")
    void response_hasReferrerPolicy() throws Exception {
        mockMvc.perform(get("/recommend"))
                .andExpect(status().isOk())
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"));
    }

    /** TLS는 리버스 프록시가 종단한다. {@code server.forward-headers-strategy}(native)가 켜져 있어도 이 앱은 기본 매처(isSecure) 대신 HSTS를 항상 붙인다(기본 매처를 그대로 썼다면 MockMvc 요청이 HTTPS가 아니라 이 테스트가 실패해야 정상이다). */
    @Test
    @DisplayName("HTTPS가 아닌 요청에도 HSTS가 붙는다(프록시 뒤에서도 항상 적용)")
    void response_hasHstsEvenOverPlainHttp() throws Exception {
        mockMvc.perform(get("/recommend"))
                .andExpect(status().isOk())
                .andExpect(header().string("Strict-Transport-Security", containsString("max-age=31536000")))
                .andExpect(header().string("Strict-Transport-Security", containsString("includeSubDomains")));
    }
}
