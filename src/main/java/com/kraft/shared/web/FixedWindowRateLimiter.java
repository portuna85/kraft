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
 * 가입·비밀번호 재설정처럼 다른 경로에서도 재사용한다. 단일 인스턴스 메모리 카운터라는 점과, 클라이언트별 "첫 요청
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
     * 추적하는 키 수의 상한(BE-02). 만료 창 제거만으로는 막을 수 없는 경우가 있다 — 공격자가 한
     * 창(windowMillis) 안에서 서로 다른 키(예: 계정 리미터의 임의 username, IPv6 주소)를 계속
     * 만들어 보내면, 그 창이 끝나기 전까지는 어떤 항목도 만료되지 않아 evictExpired가 할 일이
     * 없다. 상한을 두면 그런 경우에도 메모리가 이 수 이상으로 늘지 않는다.
     * <p>
     * 가득 찼을 때 새 키를 무조건 거절하면 그 창 동안 정상 사용자까지 막혔다(BE-13). 대신
     * {@link #makeRoom}이 "한 번만 요청하고 지나간" 키를 먼저 비워 자리를 만든다 — 한도에 걸려
     * 실제로 제한 중인 키(요청 수가 2 이상)는 건드리지 않으므로 제한이 풀리지 않는다. 비울 키가
     * 하나도 없을 때만(전부 활발히 쓰이는 중일 때) 새 키를 거절한다.
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

    /**
     * @param name          로그에 남길 이름(원시 클라이언트 키는 남기지 않는다).
     * @param limitPerWindow 창당 허용 요청 수.
     * @param windowMillis  창 길이(밀리초).
     */
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

    /**
     * 추적 중인 모든 창과 허용/거부 집계를 비운다. 싱글턴 빈의 메모리 상태를 테스트 메서드마다 처음으로
     * 되돌릴 때 쓴다 — 예전에는 그러려고 메서드마다 Spring 컨텍스트를 새로 띄웠다(OPS-10).
     */
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

    /**
     * 가득 찼을 때 새 키가 들어갈 자리를 만든다. 만료된 창을 먼저 지우고, 그래도 가득 차 있으면
     * 요청이 한 번뿐인 키를 일부 비운다. 쿨다운으로 한 번에 한 스레드만 훑게 한다.
     */
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
