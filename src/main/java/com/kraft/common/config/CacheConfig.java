package com.kraft.common.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * NOTE: Caffeine 기반 — 인스턴스별 독립 캐시(단일 인스턴스 전용, 공유 저장소 아님). 수평
 * 확장 시 인스턴스마다 별도로 채워지고 각자의 TTL로 만료되므로, 같은 요청이 어느
 * 인스턴스로 라우팅되느냐에 따라 최대 TTL(5~10분) 동안 서로 다른 스냅숏을 볼 수 있다.
 * 여기 담긴 데이터(회차·통계 요약)는 결과적 일관성으로 충분한 공개 조회 전용이라 이
 * 인스턴스 간 불일치는 허용 가능한 트레이드오프다 — 로그인/쓰기 경로에는 이 캐시를
 * 쓰지 않는다. 롤링 배포로 새 인스턴스가 뜨면 그 인스턴스는 첫 요청까지 캐시가 비어
 * 있다가(cold miss) 이후 정상적으로 채워진다.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String ROUNDS_LATEST = "rounds.latest";

    @Bean
    CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.registerCustomCache(ROUNDS_LATEST, cache(5, TimeUnit.MINUTES, 1));
        return manager;
    }

    // P-4: Caffeine 캐시 적중률을 Prometheus에 노출
    @Bean
    ApplicationRunner cacheMicroMeterBinder(CacheManager cacheManager, MeterRegistry registry) {
        return args -> cacheManager.getCacheNames().forEach(name -> {
            @SuppressWarnings("unchecked")
            Cache<Object, Object> nativeCache = (Cache<Object, Object>)
                    Objects.requireNonNull(cacheManager.getCache(name)).getNativeCache();
            CaffeineCacheMetrics.monitor(registry, nativeCache, name);
        });
    }

    private static Cache<Object, Object> cache(long duration, TimeUnit unit, long maximumSize) {
        return Caffeine.newBuilder()
                .expireAfterWrite(duration, unit)
                .maximumSize(maximumSize)
                .recordStats()
                .build();
    }
}
