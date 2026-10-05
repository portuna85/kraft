package com.kraft.home.web;

import com.kraft.config.security.SecurityConfig;
import com.kraft.post.service.PostService;
import com.kraft.recommend.domain.WinningDraw;
import com.kraft.recommend.service.LatestDrawService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/** 홈 화면: 랜딩 렌더링, 이력이 없을 때의 생략, 기능 플래그, 옛 게시판 주소의 영구 이동. */
@WebMvcTest(HomePageController.class)
@Import(SecurityConfig.class)
class HomePageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LatestDrawService latestDrawService;

    @MockitoBean
    private PostService postService;

    @MockitoBean
    private com.kraft.home.service.HomeInsightsService insightsService;

    @BeforeEach
    void emptyBoard() {
        given(insightsService.current())
                .willReturn(com.kraft.home.service.DrawInsights.of(List.of(List.of(1, 2, 3, 4, 5, 6)), 30));
        given(postService.findRecent(anyInt())).willReturn(List.of());
    }

    @Test
    @DisplayName("이력이 없으면 최신 회차 없이 랜딩과 조용한 빈 커뮤니티 안내를 그린다")
    void rendersWithoutLatestDraw() throws Exception {
        given(latestDrawService.latest()).willReturn(Optional.empty());

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("home"))
                .andExpect(model().attributeDoesNotExist("latestDraw"))
                .andExpect(content().string(containsString("데이터로 살펴보는 로또 6/45")))
                .andExpect(content().string(containsString("아직 등록된 게시글이 없습니다.")))
                .andExpect(content().string(not(containsString("당첨 확률이 높"))));
    }

    @Test
    @DisplayName("최신 회차 값은 저장소에서 읽어 그린다")
    void rendersLatestDraw() throws Exception {
        WinningDraw draw = mock(WinningDraw.class);
        given(draw.getRoundNo()).willReturn(1243);
        given(draw.numbers()).willReturn(List.of(9, 18, 24, 38, 43, 44));
        given(draw.getBonusNo()).willReturn(35);
        given(latestDrawService.latest()).willReturn(Optional.of(draw));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("latestDraw"))
                .andExpect(content().string(containsString("1243")))
                .andExpect(content().string(containsString("lotto-ball--bonus")))
                .andExpect(content().string(containsString("과거 기록 살펴보기")))
                .andExpect(content().string(containsString("다음 추첨 결과와는 관계가 없습니다")));
    }

    @Test
    @DisplayName("추첨일·당첨금·당첨자가 있으면 천 단위 구분과 세후 금액까지 그린다")
    void rendersPrizeDetails() throws Exception {
        WinningDraw draw = mock(WinningDraw.class);
        given(draw.getRoundNo()).willReturn(1243);
        given(draw.numbers()).willReturn(List.of(9, 18, 24, 38, 43, 44));
        given(draw.getDrawDate()).willReturn(java.time.LocalDate.of(2026, 9, 26));
        given(draw.getFirstPrizeAmount()).willReturn(2_592_525_282L);
        given(draw.getFirstPrizeWinnerCount()).willReturn(12);
        given(latestDrawService.latest()).willReturn(Optional.of(draw));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("2026.09.26 추첨")))
                .andExpect(content().string(containsString("2,592,525,282원")))
                .andExpect(content().string(containsString("(12명)")))
                .andExpect(content().string(containsString("세후 예상")));
    }

    @Test
    @DisplayName("홈에는 WebSite JSON-LD가 한 번 실린다")
    void rendersWebSiteJsonLd() throws Exception {
        given(latestDrawService.latest()).willReturn(Optional.empty());

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("application/ld+json")))
                .andExpect(content().string(containsString("\"@type\": \"WebSite\"")));
    }

    @Test
    @DisplayName("옛 게시판 주소(/?q=…, ?page=…)는 /community로 301 이동한다")
    void legacyBoardQueryMovesPermanently() throws Exception {
        mockMvc.perform(get("/").queryParam("q", "abc").queryParam("page", "2"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string("Location", "/community?q=abc&page=2"));
        verifyNoInteractions(latestDrawService);
    }

    @Test
    @DisplayName("추천 기능이 꺼져 있으면 최신 회차를 조회하지 않고 추천 링크도 그리지 않는다")
    void recommendDisabledHidesDrawAndCta() throws Exception {
        // 기능 플래그는 생성자 값이라 같은 슬라이스에서 끄려면 별도 컨트롤러 인스턴스가 필요하다.
        HomePageController controller = new HomePageController(latestDrawService, postService, insightsService, false);
        org.springframework.ui.ExtendedModelMap model = new org.springframework.ui.ExtendedModelMap();
        Object result = controller.home(new org.springframework.mock.web.MockHttpServletRequest(), model);

        org.assertj.core.api.Assertions.assertThat(result).isEqualTo("home");
        org.assertj.core.api.Assertions.assertThat(model.get("recommendEnabled")).isEqualTo(false);
        verifyNoInteractions(latestDrawService);
    }
}
