package com.kraft.observability;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 요청 통계를 모으는 필터. 값 자체보다 <b>무엇을 세지 않는가</b>가 중요하다.
 */
class RequestMetricsFilterTest {

    private final RequestMetrics metrics = new RequestMetrics();
    private final RequestMetricsFilter filter = new RequestMetricsFilter(metrics);

    private void request(String path, int status) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(status);
        filter.doFilter(request, response, new MockFilterChain());
    }

    @Test
    @DisplayName("상태 코드별로 4xx와 5xx를 나눠 센다")
    void countsErrorsByClass() throws Exception {
        request("/posts/1", HttpServletResponse.SC_OK);
        request("/posts/999", HttpServletResponse.SC_NOT_FOUND);
        request("/api/v1/posts", HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

        RequestMetrics.Snapshot snapshot = metrics.drain();
        assertThat(snapshot.requests()).isEqualTo(3);
        assertThat(snapshot.errors()).as("4xx + 5xx").isEqualTo(2);
        assertThat(snapshot.serverErrors()).as("5xx만").isEqualTo(1);
    }

    /**
     * 정적 자원은 수가 압도적이고 대부분 304로 끝난다. 함께 세면 실제 화면·API의 오류율이
     * 묻혀 오류율을 보는 목적 자체가 사라진다.
     */
    @Test
    @DisplayName("CSS·JS·이미지는 통계에 넣지 않는다")
    void staticResourcesAreNotCounted() throws Exception {
        request("/css/style.css", HttpServletResponse.SC_NOT_MODIFIED);
        request("/js/app/main.js", HttpServletResponse.SC_OK);
        request("/images/logo.png", HttpServletResponse.SC_OK);
        request("/favicon.ico", HttpServletResponse.SC_OK);
        request("/", HttpServletResponse.SC_OK);

        assertThat(metrics.drain().requests()).isEqualTo(1);
    }

    /**
     * 평가 보고서 2026-09-25 F12: 템플릿이 실제로 내보내는 JS 주소는 고정 버전이 앞에 붙은
     * {@code /{버전}/js/...}다. 설정된 그 버전만 정적 자원으로 보고, 모양만 비슷한 다른 경로의
     * 앱 요청·오류는 계속 센다.
     */
    @Test
    @DisplayName("F12: 설정된 버전이 붙은 JS는 세지 않고, 다른 접두어·앱 오류는 센다")
    void versionedStaticResourcesAreNotCounted() throws Exception {
        RequestMetrics versioned = new RequestMetrics();
        RequestMetricsFilter versionedFilter = new RequestMetricsFilter(versioned, "428dd13");

        for (String path : new String[] { "/428dd13/js/app/main.js", "/428dd13/js/vue-dist/chunks/runtime.js" }) {
            versionedFilter.doFilter(new MockHttpServletRequest("GET", path), response(200), new MockFilterChain());
        }
        assertThat(versioned.drain().requests()).as("버전 JS").isZero();

        versionedFilter.doFilter(new MockHttpServletRequest("GET", "/zzzzzzz/js/app/main.js"), response(404), new MockFilterChain());
        versionedFilter.doFilter(new MockHttpServletRequest("GET", "/428dd13/api/js"), response(404), new MockFilterChain());
        versionedFilter.doFilter(new MockHttpServletRequest("GET", "/api/v1/js"), response(403), new MockFilterChain());
        versionedFilter.doFilter(new MockHttpServletRequest("GET", "/posts/1"), response(500), new MockFilterChain());

        RequestMetrics.Snapshot snapshot = versioned.drain();
        assertThat(snapshot.requests()).as("버전이 다르거나 JS가 아닌 경로·앱 요청").isEqualTo(4);
        assertThat(snapshot.errors()).isEqualTo(4);
        assertThat(snapshot.serverErrors()).isEqualTo(1);
    }

    private static MockHttpServletResponse response(int status) {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(status);
        return response;
    }

    /**
     * 값을 읽어 가면 초기화한다. 누적으로 남기면 오래 켜져 있을수록 평균이 둔해져
     * "지금 느려졌는지"를 읽을 수 없다.
     */
    @Test
    @DisplayName("한 번 읽어 간 통계는 다음 주기로 넘어가지 않는다")
    void drainResetsForTheNextPeriod() throws Exception {
        request("/posts/1", HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        assertThat(metrics.drain().serverErrors()).isEqualTo(1);

        RequestMetrics.Snapshot next = metrics.drain();
        assertThat(next.requests()).isZero();
        assertThat(next.serverErrors()).isZero();
        assertThat(next.errorRate()).isZero();
    }

    @Test
    @DisplayName("예외로 끝난 요청도 세고 나서 예외를 그대로 올려보낸다")
    void requestsThatThrowAreStillCounted() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/posts/1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        MockFilterChain throwing = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                throw new IllegalStateException("처리 중 실패");
            }
        };

        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                () -> filter.doFilter(request, response, throwing))).isInstanceOf(IllegalStateException.class);
        assertThat(metrics.drain().serverErrors())
                .as("장애가 났을 때야말로 세어야 한다").isEqualTo(1);
    }
}
