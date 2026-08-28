package com.kraft.winningnumber;

import com.kraft.common.config.CacheConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * BE-CACHE-01(docs/improvement.md): {@link CacheConfig#ROUNDS_LATEST}를 회차 수집 이벤트로 비운다.
 * {@code @CacheEvict}를 이 메서드에 직접 붙인 이유: 별도 메서드로 뽑아 그 메서드 안에서 호출하면
 * 같은 클래스 내부 호출이라 프록시를 우회해 무효화가 걸리지 않는다(자기호출 함정, BE-PERF-01의
 * findLatest/getFreshness 분리와 같은 이유). TTL 5분(CacheConfig)은 이 이벤트를 놓쳤을 때의
 * 안전망이다.
 */
@Component
public class RoundsLatestCacheEvictionListener {

    private static final Logger log = LoggerFactory.getLogger(RoundsLatestCacheEvictionListener.class);

    @Async("eventTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @CacheEvict(value = CacheConfig.ROUNDS_LATEST, allEntries = true, condition = "#event.dataChanged()")
    public void onCollected(WinningNumbersCollectedEvent event) {
        if (event.dataChanged()) {
            log.info("WinningNumbersCollectedEvent 수신 — rounds.latest 캐시 비움: round={}", event.round());
        }
    }
}
