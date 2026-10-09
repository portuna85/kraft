package com.kraft.shared.web;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 임의의 키(IP·계정 등)별 고정 윈도우 요청 제한. 단일 인스턴스 메모리 카운터이며, 클라이언트별 첫 요청에 창을
 * 고정하므로 창 경계에서 최대 2배까지 통과할 수 있다.
 */
@Slf4j
public class FixedWindowRateLimiter {

    /** 만료 창을 훑는 비상 청소({@link #evictExpired})의 최소 간격 — 요청마다 O(n) 스캔이 반복되지 않게. */
    private static final long EMERGENCY_EVICT_COOLDOWN_MILLIS = 1_000L;

    /**
     * 추적하는 키 수의 상한. 한 창 안에서 서로 다른 키(임의 username, IPv6 등)를 계속 만들면 만료 청소로는
     * 못 막는다. 가득 차면 {@link #makeRoom}이 "한 번만 요청한" 키부터 비워 정상 사용자까지 막지 않고,
     * 실제로 제한 중인 키(2회 이상)는 건드리지 않는다. 비울 키가 없을 때만 새 키를 거절한다.
     */
    private static final int DEFAULT_MAX_TRACKED_CLIENTS = 50_000;

    /** 가득 찼을 때 한 번에 비우는 비율(상한의 1/20). 요청마다 O(n) 스캔을 하지 않도록 묶어서 비운다. */
    private static final int MAKE_ROOM_DIVISOR = 20;

    private final String name;
    private final int limitPerWindow;
    private final long windowMillis;
    private final int maxTrackedClients;

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final LongAdder allowed = new LongAdder();
    private final LongAdder rejected = new LongAdder();
    private final AtomicLong lastEmergencyEvictAt = new AtomicLong(0);
    private final AtomicLong lastMakeRoomAt = new AtomicLong(0);

    /** @param name 로그에 남길 이름(원시 클라이언트 키는 남기지 않는다) */
    public FixedWindowRateLimiter(String name, int limitPerWindow, long windowMillis) {
        this(name, limitPerWindow, windowMillis, DEFAULT_MAX_TRACKED_CLIENTS);
    }

    /** 테스트가 작은 상한으로 "가득 찬" 상황을 만들 수 있게 열어 둔다. */
    FixedWindowRateLimiter(String name, int limitPerWindow, long windowMillis, int maxTrackedClients) {
        this.name = name;
        this.limitPerWindow = limitPerWindow;
        this.windowMillis = windowMillis;
        this.maxTrackedClients = maxTrackedClients;
    }

    private record Window(long windowStartMillis, AtomicInteger count) {
    }

    /** 추적 중인 창과 허용/거부 집계를 비운다(싱글턴 빈의 상태를 테스트마다 초기화할 때). */
    public void reset() {
        windows.clear();
        allowed.reset();
        rejected.reset();
    }

    public boolean tryAcquire(String clientKey) {
        long now = Instant.now().toEpochMilli();

        if (!windows.containsKey(clientKey) && windows.size() >= maxTrackedClients) {
            makeRoom(now);
            if (windows.size() >= maxTrackedClients) {
                rejected.increment();
                return false;
            }
        }

        // compute()의 재매핑은 키별로 원자적이므로 이 호출이 만든 값을 그 안에서 담아 나온다
        // (밖에서 다시 읽으면 다른 요청의 증가분까지 보여 한도 안의 요청이 거절될 수 있다).
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

    /** 허용/거부 건수를 주기적으로 남겨(원시 키 제외) 한도 조정의 근거로 삼고, 만료된 항목을 청소한다. */
    public void reportAndCleanup(long now) {
        long allowedCount = allowed.sumThenReset();
        long rejectedCount = rejected.sumThenReset();
        if (allowedCount > 0 || rejectedCount > 0) {
            log.info("최근 {} 요청 제한 집계: 허용 {}건, 거부 {}건.", name, allowedCount, rejectedCount);
        }
        evictExpired(now);
    }

    /** 위임하는 다른 제한기와 테스트가 쓰도록 public. */
    public int trackedClientCount() {
        return windows.size();
    }

    private void evictIfDue(long now) {
        long last = lastEmergencyEvictAt.get();
        if (now - last >= EMERGENCY_EVICT_COOLDOWN_MILLIS && lastEmergencyEvictAt.compareAndSet(last, now)) {
            evictExpired(now);
        }
    }

    /** 가득 찼을 때 자리를 만든다: 만료 창을 먼저 지우고, 그래도 가득하면 요청이 한 번뿐인 키 일부를 비운다(쿨다운으로 한 스레드만). */
    private void makeRoom(long now) {
        long last = lastMakeRoomAt.get();
        if (now - last < EMERGENCY_EVICT_COOLDOWN_MILLIS || !lastMakeRoomAt.compareAndSet(last, now)) {
            return;
        }
        evictExpired(now);
        if (windows.size() < maxTrackedClients) {
            return;
        }
        int budget = Math.max(1, maxTrackedClients / MAKE_ROOM_DIVISOR);
        for (var entry : windows.entrySet()) {
            if (budget == 0) {
                break;
            }
            if (entry.getValue().count().get() <= 1 && windows.remove(entry.getKey(), entry.getValue())) {
                budget--;
            }
        }
    }

    private void evictExpired(long now) {
        windows.entrySet().removeIf(entry -> now - entry.getValue().windowStartMillis() >= windowMillis);
    }
}
