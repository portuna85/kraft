package com.kraft.observability;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 전체 리뷰 2026-09-26 A-SEC-03: 클라이언트가 보낸 {@code X-Request-Id}를 형식 검증 없이
 * 그대로 로그·응답에 싣지 않는다.
 */
class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @ParameterizedTest(name = "유효한 값 [{0}]은 그대로 쓴다")
    @ValueSource(strings = {
            "abc123",
            "550e8400-e29b-41d4-a716-446655440000",
            "a.b_c-D9",
    })
    @DisplayName("A-SEC-03: 허용된 형식의 요청 헤더 값은 그대로 채택한다")
    void validHeader_isKeptAsIs(String requestId) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", requestId);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getHeader("X-Request-Id")).isEqualTo(requestId);
    }

    @ParameterizedTest(name = "[{0}]은 형식에 안 맞아 새 값으로 대체한다")
    @ValueSource(strings = {
            "",
            " ",
            "has spaces",
            "한글아이디",
            "<script>alert(1)</script>",
    })
    @DisplayName("A-SEC-03: 형식에 맞지 않는 헤더 값은 새 id로 대체한다")
    void invalidHeader_isReplacedWithGeneratedId(String requestId) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", requestId);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getHeader("X-Request-Id"))
                .isNotEqualTo(requestId)
                .matches("^[A-Za-z0-9-]{36}$"); // UUID 형식
    }

    @Test
    @DisplayName("A-SEC-03: 지나치게 긴 헤더 값(64자 초과)은 새 id로 대체한다")
    void oversizedHeader_isReplaced() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "a".repeat(65));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getHeader("X-Request-Id")).hasSize(36);
    }

    @Test
    @DisplayName("헤더가 아예 없으면 새 id를 만든다")
    void missingHeader_generatesNewId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getHeader("X-Request-Id")).isNotBlank();
    }
}
