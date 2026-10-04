package com.kraft.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.cache.autoconfigure.CacheManagerCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 홈 화면 인기글처럼 요청마다 다시 계산할 필요가 없는 값을 짧게 캐시한다(BE-25,
 * {@code application.yml}의 {@code spring.cache.caffeine.spec}이 TTL·크기 상한을 정한다).
 * <p>
 * {@link JpaConfig}와 분리한 이유는 그 클래스의 주석 참고 — 여러 {@code @DataJpaTest}가
 * {@code JpaConfig}를 가져오는데, 캐시 인프라가 없는 JPA 슬라이스에 캐싱 AOP까지 켜지면
 * 컨텍스트 로딩이 깨진다. 이 클래스는 어떤 테스트 슬라이스도 가져오지 않으므로, 캐시가
 * 필요한 실제 애플리케이션 컨텍스트(전체 부팅)에만 적용된다.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * 모든 캐시가 같은 스펙(45초)을 쓰면 일주일에 한 번 바뀌는 회차 요약도 45초마다 전체 회차를 다시
     * 읽는다(BE-17). 회차 요약만 길게 두고, 이력이 반영되면 {@code HomeInsightsService}가 커밋 직후
     * 비운다. 나머지(인기글·공지)는 그대로 전역 스펙을 따른다.
     */
    @Bean
    CacheManagerCustomizer<CaffeineCacheManager> perCacheSpecs() {
        return manager -> manager.registerCustomCache("drawInsights",
                Caffeine.newBuilder().maximumSize(10).expireAfterWrite(Duration.ofHours(1)).build());
    }
}
