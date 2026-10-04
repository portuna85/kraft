package com.kraft.home.service;

import com.kraft.recommend.service.RecommendationHistoryChanged;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 회차 요약 캐시(BE-17): 길게 캐시하되, 이력이 반영되면 바로 비워야 한다. 캐시 AOP·이벤트
 * 리스너는 실제 애플리케이션 컨텍스트에서만 동작하므로 전체 부팅으로 확인한다.
 */
@SpringBootTest
class HomeInsightsCacheTest {

    @Autowired
    private HomeInsightsService service;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private ApplicationEventPublisher events;

    @Test
    @DisplayName("같은 요약은 캐시에서 다시 쓰고, 이력 변경 이벤트가 오면 비운다")
    void cachedUntilHistoryChanges() {
        DrawInsights first = service.current();
        assertThat(service.current()).as("두 번째 호출은 캐시된 같은 인스턴스").isSameAs(first);

        events.publishEvent(new RecommendationHistoryChanged(1244));

        assertThat(service.current()).as("이력이 바뀌면 새로 계산").isNotSameAs(first);
    }

    @Test
    @DisplayName("회차 요약 캐시는 전역 45초가 아니라 별도 스펙(1시간)으로 등록된다")
    void drawInsightsUsesItsOwnSpec() {
        var cache = (com.github.benmanes.caffeine.cache.Cache<?, ?>) cacheManager.getCache("drawInsights").getNativeCache();

        var expiry = cache.policy().expireAfterWrite().orElseThrow();
        assertThat(expiry.getExpiresAfter().toMinutes()).isEqualTo(60);
    }
}
