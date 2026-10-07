package com.kraft.shared.web;

import com.kraft.config.security.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code /}는 화면이 아니라 입구다 — 번호 추천으로 보내고, 옛 게시판 주소만 /community로 영구 이동시킨다. */
@WebMvcTest(RootRedirectController.class)
@Import(SecurityConfig.class)
class RootRedirectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("/는 번호 추천으로 임시 이동한다")
    void rootRedirectsToRecommend() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/recommend"));
    }

    @Test
    @DisplayName("옛 게시판 주소(/?q=…, ?page=…)는 /community로 301 이동한다")
    void legacyBoardQueryMovesPermanently() throws Exception {
        mockMvc.perform(get("/").queryParam("q", "abc").queryParam("page", "2"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string("Location", "/community?q=abc&page=2"));
    }

    @Test
    @DisplayName("게시판과 무관한 쿼리가 붙은 /는 번호 추천으로 임시 이동한다")
    void unrelatedQueryStillRedirectsToRecommend() throws Exception {
        mockMvc.perform(get("/").queryParam("utm_source", "x"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/recommend"));
    }

    @Test
    @DisplayName("추천 기능이 꺼져 있으면 /는 커뮤니티로 이동한다")
    void recommendDisabledRedirectsToCommunity() {
        // 기능 플래그는 생성자 값이라 같은 슬라이스에서 끄려면 별도 컨트롤러 인스턴스가 필요하다.
        var response = new RootRedirectController(false).root(new MockHttpServletRequest());

        assertThat(response.getStatusCode().value()).isEqualTo(302);
        assertThat(response.getHeaders().getLocation()).hasToString("/community");
    }
}
