package com.kraft.shared.web;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 임의의 키(IP·계정 등)에 대해 고정 윈도우 방식으로 분당(또는 지정한 창) 요청 수를 제한하는
 * 순수 유틸리티. 원래 {@code RecommendationRateLimiter}에만 있던 로직을 그대로 옮겨, 로그인·
 * 가입·비밀번호 재설정처럼 다른 경로에서도 재사용한다(개선 보고서 SEC-01,
 * {@code AuthRateLimitFilter}). 단일 인스턴스 메모리 카운터라는 점과, 클라이언트별 "첫 요청
 * 시각"에 창을 고정하는 고정 윈도우 특성(창 경계에서 최대 2배까지 통과할 수 있음)은 원본과
 * 동일하게 유지한다.
 */
@Slf4j
public class FixedWindowRateLimiter {

    /**
     * map 전체를 훑는 비상 청소({@link #evictExpired})가 요청량에 비례해 반복 실행되지 않도록
     * 최소 이 간격을 둔다. {@code windows.size() > 10_000}인 동안에는 그 뒤에 오는 모든 요청이
     * 매번 O(n) 스캔을 유발할 수 있었다 — 쿨다운으로 한 번에 한 스레드만 실제로 훑게 한다.
     */
    private static final long EMERGENCY_EVICT_COOLDOWN_MILLIS = 1_000L;

    /**
     * 처음 보는 키가 이 수를 넘어서면 새 창을 만들지 않고 거절한다(BE-02). 만료 창 제거만으로는
     * 막을 수 없는 경우가 있다 — 공격자가 한 창(windowMillis) 안에서 서로 다른 키(예: 계정
     * 리미터의 임의 username)를 계속 만들어 보내면, 그 창이 끝나기 전까지는 어떤 항목도 만료되지
     * 않아 evictExpired가 할 일이 없다. 상한을 두면 그런 경우에도 메모리가 이 수 이상으로 늘지
     * 않는다 — 대가로 그 이후의 새 키는 (다른 공격자의 정상 요청이라도) 이번 창 동안 거절된다.
     */
    private static final int MAX_TRACKED_CLIENTS = 50_000;

    private final String name;
    private final int limitPerWindow;
    private final long windowMillis;

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final LongAdder allowed = new LongAdder();
    private final LongAdder rejected = new LongAdder();
    private final AtomicLong lastEmergencyEvictAt = new AtomicLong(0);

    /**
     * @param name          로그에 남길 이름(원시 클라이언트 키는 남기지 않는다).
     * @param limitPerWindow 창당 허용 요청 수.
     * @param windowMillis  창 길이(밀리초).
     */
    public FixedWindowRateLimiter(String name, int limitPerWindow, long windowMillis) {
        this.name = name;
        this.limitPerWindow = limitPerWindow;
        this.windowMillis = windowMillis;
    }

    private record Window(long windowStartMillis, AtomicInteger count) {
    }

    public boolean tryAcquire(String clientKey) {
        long now = Instant.now().toEpochMilli();

        if (!windows.containsKey(clientKey) && windows.size() >= MAX_TRACKED_CLIENTS) {
            evictIfDue(now);
            if (windows.size() >= MAX_TRACKED_CLIENTS) {
                rejected.increment();
                return false;
            }
        }

        // compute()의 재매핑 함수는 키별로 원자적이므로, "이 호출이 실제로 만든 값"을 그 안에서
        // 직접 담아 나온다 — 바깥에서 공유 AtomicInteger를 따로 다시 읽으면, 그 사이 다른 요청이
        // 같은 키를 또 증가시켜 이 호출이 만든 값보다 큰 수를 보게 되고, 한도 안에서 들어온
        // 요청도 거절될 수 있었다.
        int[] countAfterThisCall = new int[1];
        windows.compute(clientKey, (key, existing) -> {
            if (existing == null || now - existing.windowStartMillis() >= windowMillis) {
                countAfterThisCall[0] = 1;
                return new Window(now, new AtomicInteger(1));
            }
            countAfterThisCall[0] = existing.count().incrementAndGet();
            return existing;
        });

        if (windows.size() > 10_000) {
            evictIfDue(now);
        }

        boolean withinLimit = countAfterThisCall[0] <= limitPerWindow;
        if (withinLimit) {
            allowed.increment();
        } else {
            rejected.increment();
        }
        return withinLimit;
    }

    /**
     * 실측 없이는 한도가 맞는 값인지 알 수 없다 — 허용/거부 건수를 주기적으로 남겨 나중에 조정
     * 여부를 판단할 근거로 삼는다(원시 키는 남기지 않는다). 같은 주기에 만료된 항목을 무조건
     * 청소해, {@code windows.size() > 10_000} 문턱 아래에서도 스쳐 지나간 클라이언트의 항목이
     * 무한히 쌓이지 않게 한다.
     */
    public void reportAndCleanup(long now) {
        long allowedCount = allowed.sumThenReset();
        long rejectedCount = rejected.sumThenReset();
        if (allowedCount > 0 || rejectedCount > 0) {
            log.info("최근 {} 요청 제한 집계: 허용 {}건, 거부 {}건.", name, allowedCount, rejectedCount);
        }
        evictExpired(now);
    }

    /** 다른 패키지의 제한기(RecommendationRateLimiter 등)가 위임하고, 테스트가 청소 후 남은
     * 클라이언트 수를 확인할 수 있게 public으로 둔다. */
    public int trackedClientCount() {
        return windows.size();
    }

    private void evictIfDue(long now) {
        long last = lastEmergencyEvictAt.get();
        if (now - last >= EMERGENCY_EVICT_COOLDOWN_MILLIS && lastEmergencyEvictAt.compareAndSet(last, now)) {
            evictExpired(now);
        }
    }

    private void evictExpired(long now) {
        windows.entrySet().removeIf(entry -> now - entry.getValue().windowStartMillis() >= windowMillis);
    }
}
