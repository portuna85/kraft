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
 * KF-10(docs/improvement.md) — 관리자 전체 백필 확인창이 자체 CSP에 막히던 문제를
 * 고쳤다.
 *
 * {@code admin/dashboard.html}과 {@code admin/rounds.html}의 파괴적 작업 폼은
 * 더 이상 {@code onsubmit="return confirm(...)"} 인라인 핸들러에 기대지 않는다.
 * 대신 {@code data-confirm} 속성 + 같은 출처 외부 스크립트({@code admin-confirm.js})
 * 조합으로 바꿨다 — {@link AdminSecurityConfig}의 CSP가 {@code script-src}를 따로
 * 열지 않아도(즉 {@code 'unsafe-inline'}이 없어도) 같은 출처 외부 스크립트는
 * {@code default-src 'self'}가 원래부터 허용하므로 CSP 자체는 건드리지 않았다.
 *
 * <p>이 테스트는 세 가지를 단언한다: (a) CSP는 여전히 인라인 스크립트를 막고
 * 있고(회귀 방지), (b) 응답 본문에 더 이상 {@code onsubmit=}이 없으며, (c) 본문이
 * {@code data-confirm} 속성과 {@code admin-confirm.js} 참조를 통해 실제로 확인창
 * 메커니즘을 갖추고 있다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("KF-10: 관리자 확인창이 CSP 아래에서도 동작한다(외부 스크립트 + data-confirm)")
class AdminCspInlineScriptTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("대시보드: CSP는 인라인 스크립트를 막고, 본문은 onsubmit 대신 data-confirm + 외부 스크립트를 쓴다")
    void dashboard_cspForbidsInlineScript_bodyUsesDataConfirm() throws Exception {
        MockHttpServletResponse response = mockMvc
                .perform(get("/admin/dashboard").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        assertCspForbidsInlineScript(response);
        assertConfirmationUsesDataConfirm(response);
    }

    @Test
    @DisplayName("회차 관리: CSP는 인라인 스크립트를 막고, 본문은 onsubmit 대신 data-confirm + 외부 스크립트를 쓴다")
    void rounds_cspForbidsInlineScript_bodyUsesDataConfirm() throws Exception {
        MockHttpServletResponse response = mockMvc
                .perform(get("/admin/rounds").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        assertCspForbidsInlineScript(response);
        assertConfirmationUsesDataConfirm(response);
    }

    private static void assertConfirmationUsesDataConfirm(MockHttpServletResponse response) throws Exception {
        String body = response.getContentAsString();
        assertThat(body).doesNotContain("onsubmit=");
        assertThat(body).contains("data-confirm=");
        assertThat(body).contains("admin-confirm.js");
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
