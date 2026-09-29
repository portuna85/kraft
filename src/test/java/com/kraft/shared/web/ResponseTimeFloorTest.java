package com.kraft.shared.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ResponseTimeFloorTest {

    @Test
    @DisplayName("작업이 하한보다 빨리 끝나면 하한까지 기다린다")
    void awaitsUntilFloor() {
        long start = System.nanoTime();

        ResponseTimeFloor.await(start, 120);

        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)).isGreaterThanOrEqualTo(115);
    }

    @Test
    @DisplayName("이미 하한을 넘겼거나 하한이 0 이하면 기다리지 않는다")
    void doesNotWaitWhenPastFloorOrDisabled() {
        long longAgo = System.nanoTime() - TimeUnit.SECONDS.toNanos(5);
        long before = System.nanoTime();

        ResponseTimeFloor.await(longAgo, 250);
        ResponseTimeFloor.await(System.nanoTime(), 0);

        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before)).isLessThan(100);
    }
}
