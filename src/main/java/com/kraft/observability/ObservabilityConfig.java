package com.kraft.observability;

import com.kraft.post.domain.PostImageRepository;
import com.kraft.recommend.domain.RecommendationHistoryStateRepository;
import com.kraft.recommend.service.RecommendationFetchStatus;
import com.kraft.user.mail.EmailSender;
import com.kraft.user.mail.OutboxMailRepository;
import com.kraft.user.session.SessionRevocationTaskRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import javax.sql.DataSource;
import java.time.Duration;

/**
 * 관측 구성요소를 한곳에서 조립한다. {@code @Component} 대신 {@code @Configuration}에 모으는 이유는 슬라이스 테스트다 —
 * {@code @WebMvcTest}는 {@code Filter} 빈만 끌어오고 일반 컴포넌트는 가져오지 않아, 필터만 컴포넌트였을 때
 * {@code RequestMetrics}를 못 찾아 웹 슬라이스 테스트 60여 개가 깨졌다. {@code @Configuration}은 슬라이스에서 제외된다.
 */
@Configuration
@EnableConfigurationProperties(MetricsProperties.class)
public class ObservabilityConfig {

    @Bean
    public RequestMetrics requestMetrics(@Value("${app.metrics.slow-request-ms:3000}") long slowThresholdMillis) {
        return new RequestMetrics(slowThresholdMillis);
    }

    /**
     * 보안 필터 체인(order -100)보다 앞에 둔다(인증 실패·CSRF 거부 응답도 통계에 잡히게). {@link RequestIdFilter}보다
     * 하나 뒤라야 이 필터를 포함한 모든 로거가 상관관계 id를 MDC에서 본다.
     */
    @Bean
    public FilterRegistrationBean<RequestMetricsFilter> requestMetricsFilter(
            RequestMetrics metrics,
            @Value("${spring.web.resources.chain.strategy.fixed.version:}") String staticResourceVersion) {
        FilterRegistrationBean<RequestMetricsFilter> registration =
                new FilterRegistrationBean<>(new RequestMetricsFilter(metrics, staticResourceVersion));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }

    /** 요청마다 상관관계 id를 MDC에 심는다({@link RequestIdFilter}). 다른 필터·로거보다 먼저여야 해 가장 이른 순서다. */
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
                                         SessionRevocationTaskRepository sessionRevocationTaskRepository,
                                         PostImageRepository postImageRepository,
                                         RecommendationHistoryStateRepository recommendationHistoryStateRepository,
                                         DataSource dataSource,
                                         @Value("${app.upload.dir}") String uploadDir,
                                         AlertMailer alertMailer,
                                         MetricsProperties properties,
                                         @Value("${app.recommend.enabled:true}") boolean recommendEnabled,
                                         RecommendationFetchStatus recommendationFetchStatus) {
        return new HealthReporter(metrics, outboxMailRepository,
                sessionRevocationTaskRepository, postImageRepository, recommendationHistoryStateRepository,
                dataSource, uploadDir, alertMailer, properties, recommendEnabled, recommendationFetchStatus);
    }

    /** {@code app.metrics.alert-email}이 비어 있으면 {@link AlertMailer#disabled()}와 같이 조용히 꺼진다. */
    @Bean
    public AlertMailer alertMailer(EmailSender emailSender,
                                   @Value("${app.metrics.alert-email:}") String alertEmail,
                                   @Value("${app.metrics.alert-cooldown-ms:3600000}") long cooldownMs) {
        return new AlertMailer(emailSender, alertEmail, Duration.ofMillis(cooldownMs));
    }
}
