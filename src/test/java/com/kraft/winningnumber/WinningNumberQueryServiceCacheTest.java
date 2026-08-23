package com.kraft.winningnumber;

import static org.awaitility.Awaitility.await;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.kraft.Application;
import com.kraft.common.config.CacheConfig;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * BE-CACHE-01(docs/improvement.md): {@code findLatest()}의 {@code @Cacheable(ROUNDS_LATEST)}가
 * 실제로 걸리는지 검증한다. {@link WinningNumberQueryServiceTest}는 서비스를 {@code new}로 직접
 * 생성해 Spring AOP 프록시를 거치지 않으므로 캐싱을 재현할 수 없다 — 실제 프록시가 걸린 빈이
 * 필요해 별도 {@code @SpringBootTest} 클래스로 분리한다.
 */
@SpringBootTest(classes = Application.class)
@ActiveProfiles("test")
@DisplayName("당첨 번호 조회 서비스 — rounds.latest 캐시 테스트")
class WinningNumberQueryServiceCacheTest {

    @Autowired
    private WinningNumberQueryService winningNumberQueryService;

    @Autowired
    private RoundsLatestCacheEvictionListener roundsLatestCacheEvictionListener;

    @Autowired
    private CacheManager cacheManager;

    @MockitoBean
    private WinningNumberRepository winningNumberRepository;

    @BeforeEach
    void clearCache() {
        // 이 클래스의 테스트들은 같은 Spring 컨텍스트(따라서 같은 싱글턴 CacheManager)를
        // 공유한다 — 이전 테스트가 채워 놓은 rounds.latest 항목이나 이전 테스트의 verify() 호출
        // 횟수가 남아 있으면 이 클래스의 절대 호출 횟수 단정이 실행 순서에 좌우된다. 매 테스트
        // 시작 전에 캐시와 목 둘 다 초기화한다.
        Objects.requireNonNull(cacheManager.getCache(CacheConfig.ROUNDS_LATEST)).clear();
        Mockito.reset(winningNumberRepository);
    }

    @Test
    @DisplayName("같은 회차를 두 번 조회하면 두 번째는 리포지토리를 다시 타지 않는다")
    void findLatest_secondCall_hitsCacheNotRepository() {
        given(winningNumberRepository.findTopByOrderByRoundDesc()).willReturn(Optional.of(
                WinningNumberTestFactory.create(1, LocalDate.of(2026, 6, 20), 5, 12, 18, 27, 36, 44, 7,
                        1_000_000_000L, 0L, 0, 0L, 0L, OffsetDateTime.now(ZoneOffset.UTC))));

        winningNumberQueryService.findLatest();
        winningNumberQueryService.findLatest();

        verify(winningNumberRepository, times(1)).findTopByOrderByRoundDesc();
    }

    @Test
    @DisplayName("BE-CACHE-01: dataChanged 이벤트 수신 후에는 캐시가 비어 다시 리포지토리를 탄다")
    void onCollected_dataChanged_evictsCache() {
        given(winningNumberRepository.findTopByOrderByRoundDesc()).willReturn(Optional.of(
                WinningNumberTestFactory.create(1, LocalDate.of(2026, 6, 20), 5, 12, 18, 27, 36, 44, 7,
                        1_000_000_000L, 0L, 0, 0L, 0L, OffsetDateTime.now(ZoneOffset.UTC))));
        winningNumberQueryService.findLatest();

        // 자동 주입된 빈(프록시)에서 바로 호출한다 — 같은 클래스 안에서 부르면 자기호출 함정에
        // 걸려 @CacheEvict가 안 걸린다는 것이 BE-CACHE-01의 핵심 교훈이므로, 테스트도 반드시
        // 외부(여기서는 이 테스트 메서드)에서 프록시를 거쳐 호출해야 실제 배선을 검증한다.
        // @Async가 이 메서드 전체(캐시 무효화 관점 포함)를 eventTaskExecutor로 넘기므로 이 호출은
        // 즉시 반환된다 — 실제 무효화는 별도 스레드에서 비동기로 일어나므로 Awaitility로 기다린다.
        roundsLatestCacheEvictionListener.onCollected(new WinningNumbersCollectedEvent(1, true));

        await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            winningNumberQueryService.findLatest();
            verify(winningNumberRepository, times(2)).findTopByOrderByRoundDesc();
        });
    }

    @Test
    @DisplayName("BE-CACHE-01: dataChanged=false 이벤트는 캐시를 비우지 않는다")
    void onCollected_dataNotChanged_doesNotEvictCache() {
        given(winningNumberRepository.findTopByOrderByRoundDesc()).willReturn(Optional.of(
                WinningNumberTestFactory.create(1, LocalDate.of(2026, 6, 20), 5, 12, 18, 27, 36, 44, 7,
                        1_000_000_000L, 0L, 0, 0L, 0L, OffsetDateTime.now(ZoneOffset.UTC))));
        winningNumberQueryService.findLatest();

        roundsLatestCacheEvictionListener.onCollected(new WinningNumbersCollectedEvent(1, false));

        // 무효화가 "일어나지 않음"은 즉시 관측할 수 없다(비동기 작업이 도는 중일 수 있음) —
        // 위 테스트와 같은 실행 예산만큼 기다린 뒤에도 캐시가 여전히 살아 있는지(=재조회) 확인한다.
        await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            winningNumberQueryService.findLatest();
            verify(winningNumberRepository, times(1)).findTopByOrderByRoundDesc();
        });
    }
}
