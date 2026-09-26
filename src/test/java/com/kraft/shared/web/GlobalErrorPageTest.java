package com.kraft.shared.web;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * {@code BasicErrorController}가 {@code templates/error/4xx.html}·{@code error/5xx.html}로
 * 응답하는지 확인한다(13단계, 전역 오류 화면 — 지금까지는 게시글을 찾을 수 없을 때
 * (ViewExceptionHandler → error/not-found)만 안내 화면이 있었고, 403·잘못된 요청·서버
 * 오류 등 나머지는 Spring 기본 whitelabel 페이지가 그대로 나가고 있었다).
 * <p>
 * 실제 "핸들러 없음" 404가 컨테이너 수준에서 {@code /error}로 재디스패치되는 과정은
 * MockMvc의 목(mock) 서블릿 환경에서 재현되지 않는다 — 그래서 {@code DefaultErrorAttributes}가
 * 읽는 요청 속성({@code jakarta.servlet.error.*})을 직접 채운 뒤 {@code /error}를 호출해
 * {@code BasicErrorController}만 떼어 검증한다. 이는 Spring 자체 문서가 권장하는 커스텀
 * 오류 페이지 테스트 방식이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GlobalErrorPageTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 존재하지_않는_화면_경로는_공통_404_안내를_보여준다() throws Exception {
        mockMvc.perform(get("/error")
                        .accept(MediaType.TEXT_HTML)
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/this-path-does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/4xx"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("페이지를 찾을 수 없습니다")))
                .andExpect(content().string(containsString("게시판으로 돌아가기")))
                // 내부 정보(메시지·스택 트레이스)를 화면에 그대로 내보내지 않는다.
                .andExpect(content().string(not(containsString("stackTrace"))));
    }

    @Test
    void 권한이_없는_403은_403_전용_안내를_보여준다() throws Exception {
        mockMvc.perform(get("/error")
                        .accept(MediaType.TEXT_HTML)
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 403)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/admin/reports"))
                .andExpect(status().isForbidden())
                .andExpect(view().name("error/4xx"))
                .andExpect(content().string(containsString("접근 권한이 없습니다")));
    }

    @Test
    void 서버_오류는_5xx_안내를_보여준다() throws Exception {
        mockMvc.perform(get("/error")
                        .accept(MediaType.TEXT_HTML)
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/"))
                .andExpect(status().isInternalServerError())
                .andExpect(view().name("error/5xx"))
                .andExpect(content().string(containsString("일시적인 오류")))
                .andExpect(content().string(not(containsString("stackTrace"))));
    }

    @Test
    void api_경로의_오류는_여전히_json으로_응답한다() throws Exception {
        mockMvc.perform(get("/error")
                        .accept(MediaType.APPLICATION_JSON)
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/api/v1/this-path-does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }
}
