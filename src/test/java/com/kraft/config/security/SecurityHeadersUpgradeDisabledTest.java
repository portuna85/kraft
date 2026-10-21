package com.kraft.config.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * e2e 프로파일처럼 {@code app.security.csp-upgrade-insecure-requests=false}이면 {@code upgrade-insecure-requests}만 빠지고
 * 나머지 CSP는 그대로인지 확인한다. 기본값(켜짐)은 {@link SecurityHeadersTest}가 지킨다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.security.csp-upgrade-insecure-requests=false")
class SecurityHeadersUpgradeDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("끄면 upgrade-insecure-requests만 빠지고 나머지 CSP는 유지된다")
    void disabled_dropsOnlyUpgradeDirective() throws Exception {
        mockMvc.perform(get("/recommend"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", not(containsString("upgrade-insecure-requests"))))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")))
                .andExpect(header().string("Content-Security-Policy", containsString("style-src 'self'")))
                .andExpect(header().string("Content-Security-Policy", containsString("object-src 'none'")));
    }
}
