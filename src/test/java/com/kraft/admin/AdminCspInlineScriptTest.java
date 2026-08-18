package com.kraft.admin;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * KF-10(docs/improvement.md) — 관리자 전체 백필 확인창이 자체 CSP에 막힌다.
 *
 * {@code admin/dashboard.html}과 {@code admin/rounds.html}의 파괴적 작업 폼은
 * {@code onsubmit="return confirm(...)"} 인라인 이벤트 핸들러에 기대는데,
 * {@link AdminSecurityConfig}의 CSP는 {@code script-src}를 별도로 열지 않아
 * {@code default-src 'self'}가 그대로 적용된다 — {@code 'unsafe-inline'}이 없으므로
 * 표준을 지키는 브라우저는 인라인 핸들러를 차단한다. 즉 확인 없이 폼이 제출된다.
 *
 * 헤드리스 브라우저로 "confirm()이 실제로 안 뜬다"까지 확인하려면 Spring 관리자
 * 서버를 대상으로 하는 새 Playwright 트랙이 필요해 1단계 범위를 벗어난다. 대신
 * 결함을 구성하는 두 사실 — (a) CSP가 인라인 스크립트를 허용하지 않고 (b) 응답
 * 본문이 그 인라인 스크립트에 기댄다 — 을 하나의 테스트에서 함께 단언한다.
 *
 * <p><b>이 테스트는 지금 통과해야 정상이다(두 조건이 모두 참이라는 것 자체가 결함의
 * 재현이다).</b> CSP에 nonce/hash 기반 script-src를 추가하거나 인라인 핸들러를
 * 제거하는 수정이 들어가면 이 테스트 중 한쪽 단언이 깨지므로, 그때 테스트를 함께
 * 갱신한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("KF-10: 관리자 CSP가 인라인 onsubmit을 차단하는데 페이지는 그것에 의존한다")
class AdminCspInlineScriptTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("대시보드: CSP는 인라인 스크립트를 허용하지 않지만 본문은 onsubmit에 의존한다")
    void dashboard_cspForbidsInlineScript_butBodyUsesOnsubmit() throws Exception {
        MockHttpServletResponse response = mockMvc
                .perform(get("/admin/dashboard").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        assertCspForbidsInlineScript(response);
        assertThat(response.getContentAsString()).contains("onsubmit=");
    }

    @Test
    @DisplayName("회차 관리: CSP는 인라인 스크립트를 허용하지 않지만 본문은 onsubmit에 의존한다")
    void rounds_cspForbidsInlineScript_butBodyUsesOnsubmit() throws Exception {
        MockHttpServletResponse response = mockMvc
                .perform(get("/admin/rounds").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        assertCspForbidsInlineScript(response);
        assertThat(response.getContentAsString()).contains("onsubmit=");
    }

    private static void assertCspForbidsInlineScript(MockHttpServletResponse response) {
        List<String> cspHeaders = response.getHeaders("Content-Security-Policy");
        assertThat(cspHeaders).isNotEmpty();
        String csp = String.join("; ", cspHeaders);

        // script-src를 별도로 선언하지 않았다면 default-src가 스크립트에도 적용된다
        // (CSP fallback 규칙) — 그 default-src에 'unsafe-inline'이 없어야 인라인
        // 이벤트 핸들러가 실제로 차단된다.
        boolean hasScriptSrc = csp.contains("script-src");
        String governingDirective = hasScriptSrc
                ? extractDirective(csp, "script-src")
                : extractDirective(csp, "default-src");

        assertThat(governingDirective).doesNotContain("'unsafe-inline'");
    }

    private static String extractDirective(String csp, String directiveName) {
        for (String part : csp.split(";")) {
            String trimmed = part.trim();
            if (trimmed.startsWith(directiveName)) {
                return trimmed;
            }
        }
        return "";
    }
}
