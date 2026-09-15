package com.kraft.config.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 정적 자원(CSS·JS)이 페이지 응답과 다른 캐시 헤더를 받는지 확인한다.
 * <p>
 * 예전에는 {@code SecurityConfig}의 단일 필터 체인이 모든 응답에
 * {@code Cache-Control: no-store}를 붙여, {@code application.yml}의
 * {@code spring.web.resources.cache} 설정이 있어도 브라우저가 CSS·JS를 매 페이지 이동마다
 * 다시 받았다. {@code staticResourceChain}이 그 경로에서만 캐시 헤더 라이터를 끄는 것을
 * 이 테스트가 고정한다 — 반대로 HTML 응답은 여전히 {@code no-store}여야 로그인 여부에 따라
 * 다른 화면이 캐시되는 사고가 나지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class StaticResourceCacheTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("CSS 응답에는 no-store가 없다")
    void cssResponse_hasNoStoreRemoved() throws Exception {
        mockMvc.perform(get("/css/style.css"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", not(containsString("no-store"))));
    }

    @Test
    @DisplayName("JS 응답에는 no-store가 없다")
    void jsResponse_hasNoStoreRemoved() throws Exception {
        mockMvc.perform(get("/js/app/main.js"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", not(containsString("no-store"))));
    }

    @Test
    @DisplayName("HTML 응답은 그대로 no-store다 — 로그인 여부에 따라 다른 화면이 캐시되면 안 된다")
    void htmlResponse_stillHasNoStore() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }
}
