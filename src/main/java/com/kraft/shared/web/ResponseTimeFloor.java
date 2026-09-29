package com.kraft.shared.web;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * 응답 시간의 하한을 맞춰 "가입된 주소인가"에 따른 처리 시간 차이(타이밍 부채널, P1-8)를 가린다.
 * 완벽한 상수 시간이 목표가 아니라, 분기별 작업 시간의 큰 격차(DB 쓰기·토큰 생성 유무)를
 * 하한 아래로 묻는 것이 목적이다. 이 앱은 가상 스레드를 쓰므로 대기 비용이 작다 — 단,
 * 트랜잭션·커넥션을 쥔 채로 부르지 말고 서비스 호출이 끝난 뒤(컨트롤러)에서 부른다.
 */
public final class ResponseTimeFloor {

    private ResponseTimeFloor() {
    }

    /** {@code startNanos}({@link System#nanoTime()}) 이후 {@code minMillis}가 지날 때까지 기다린다. */
    public static void await(long startNanos, long minMillis) {
        if (minMillis <= 0) {
            return;
        }
        long remaining = TimeUnit.MILLISECONDS.toNanos(minMillis) - (System.nanoTime() - startNanos);
        if (remaining > 0) {
            LockSupport.parkNanos(remaining);
        }
    }
}
