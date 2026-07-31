package com.kraft.common.web;

import java.util.OptionalInt;

/**
 * 고정 윈도(fixed-window) 레이트리밋 카운터. PublicRateLimitFilter(IP 키)와
 * CommunityWriteRateLimitFilter(사용자 ID 키)가 공유한다 — 키 충돌을 막기 위해
 * 호출자가 접두어로 네임스페이스를 구분해야 한다(예: "ratelimit:public:").
 *
 * kraft.security.rate-limit-backend로 구현체를 선택한다: in-memory(기본, Caffeine,
 * 단일 인스턴스 전용) 또는 redis(다중 인스턴스 공유, InMemoryRateLimitCounter의
 * 대체 — RedisRateLimitCounter 참고).
 */
public interface RateLimitCounter {

    /**
     * key에 대한 카운터를 1 증가시키고 증가 후 값을 반환한다. 이 key로 처음 호출되면
     * windowSeconds 뒤 자동 만료되도록 설정한다.
     *
     * 반환값이 비어 있으면(OptionalInt.empty()) 카운터 백엔드를 신뢰할 수 없는 상태다
     * (예: Redis 연결 실패) — 호출자는 이 경우 한도 검사를 건너뛰고 요청을 통과시켜야
     * 한다(fail-open). 레이트리밋 자체의 장애가 서비스 전체 장애로 번지지 않게 하기
     * 위함이다.
     */
    OptionalInt incrementAndGet(String key, int windowSeconds);
}
