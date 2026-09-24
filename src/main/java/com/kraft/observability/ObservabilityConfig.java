package com.kraft.observability;

import com.kraft.post.domain.PostImageRepository;
import com.kraft.recommend.domain.RecommendationHistoryStateRepository;
import com.kraft.report.domain.ReportRepository;
import com.kraft.user.mail.OutboxMailRepository;
import com.kraft.user.session.SessionRevocationTaskRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import javax.sql.DataSource;

/**
 * 관측 구성요소를 한곳에서 조립한다.
 * <p>
 * 이들을 {@code @Component}로 두지 않는 이유는 <b>슬라이스 테스트</b> 때문이다.
 * {@code @WebMvcTest}는 컨트롤러와 함께 {@code Filter} 빈을 끌어오지만 일반 컴포넌트는
 * 가져오지 않는다. 필터만 @Component였을 때 {@code RequestMetrics}를 찾지 못해 웹 슬라이스
 * 테스트 60여 개가 컨텍스트 로딩 단계에서 전부 깨졌다. {@code @Configuration}은 슬라이스에서
 * 통째로 제외되므로, 여기 모아 두면 관측 장치가 웹 슬라이스에 끼어들지 않는다.
 */
@Configuration
public class ObservabilityConfig {

    @Bean
    public RequestMetrics requestMetrics(@Value("${app.metrics.slow-request-ms:3000}") long slowThresholdMillis) {
        return new RequestMetrics(slowThresholdMillis);
    }

    /**
     * 보안 필터 체인(order -100)보다 <b>앞</b>에 둔다. 뒤에 두면 인증 실패·CSRF 거부처럼 필터
     * 단계에서 끝나는 응답이 통계에 잡히지 않는다 — 세션이 통째로 깨져 403이 쏟아지는 상황이
     * 바로 알아야 할 상황인데, 그때 오히려 지표가 조용해진다.
     * <p>
     * {@link RequestIdFilter}보다는 하나 뒤 순서다(BE-28) — 그래야 이 필터를 포함해 이 요청이
     * 지나가는 모든 로거가 상관관계 id를 이미 MDC에서 볼 수 있다.
     */
    @Bean
    public FilterRegistrationBean<RequestMetricsFilter> requestMetricsFilter(RequestMetrics metrics) {
        FilterRegistrationBean<RequestMetricsFilter> registration =
                new FilterRegistrationBean<>(new RequestMetricsFilter(metrics));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }

    /**
     * 요청마다 상관관계 id를 MDC에 심는다(BE-28, {@link RequestIdFilter}). 다른 모든 필터·
     * 로거보다 먼저 실행되어야 하므로 가장 이른 순서를 그대로 쓴다.
     */
    @Bean
    public FilterRegistrationBean<RequestIdFilter> requestIdFilter() {
        FilterRegistrationBean<RequestIdFilter> registration =
                new FilterRegistrationBean<>(new RequestIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    public HealthReporter healthReporter(RequestMetrics metrics,
                                         OutboxMailRepository outboxMailRepository,
                                         ReportRepository reportRepository,
                                         SessionRevocationTaskRepository sessionRevocationTaskRepository,
                                         PostImageRepository postImageRepository,
                                         RecommendationHistoryStateRepository recommendationHistoryStateRepository,
                                         DataSource dataSource,
                                         @Value("${app.upload.dir}") String uploadDir) {
        return new HealthReporter(metrics, outboxMailRepository, reportRepository,
                sessionRevocationTaskRepository, postImageRepository, recommendationHistoryStateRepository,
                dataSource, uploadDir);
    }
}
