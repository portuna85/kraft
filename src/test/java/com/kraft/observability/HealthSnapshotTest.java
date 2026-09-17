package com.kraft.observability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 임계 판정만 따로 본다. 수집(DB·디스크·풀)과 분리돼 있어 여기서는 값만 넣어 확인한다.
 * <p>
 * 이 테스트가 지키는 것은 "언제 사람을 부를지"다. 너무 예민하면 아무도 로그를 안 보게 되고,
 * 너무 둔하면 있으나 마나 하다.
 */
class HealthSnapshotTest {

    private static final HealthThresholds LIMITS = new HealthThresholds(
            20, 0.1, 0, 1000, 0.8, 1_073_741_824L, 20, 0, 20, 5);

    private static HealthSnapshot healthy() {
        return new HealthSnapshot(100, 2, 0, 120, 400, 2, 10, 0, 50_000_000_000L, 0, 0, 0, 0);
    }

    @Test
    @DisplayName("정상 범위에서는 아무것도 걸리지 않는다")
    void healthySnapshotHasNoBreaches() {
        assertThat(healthy().breaches(LIMITS)).isEmpty();
    }

    @Test
    @DisplayName("오류율이 기준을 넘으면 수치와 기준을 함께 남긴다")
    void highErrorRateIsReported() {
        HealthSnapshot snapshot = new HealthSnapshot(100, 30, 0, 120, 400, 2, 10, 0, 50_000_000_000L, 0, 0, 0, 0);

        assertThat(snapshot.breaches(LIMITS))
                .singleElement(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .contains("30.0%")
                .contains("기준 10.0%");
    }

    /**
     * 표본이 적으면 비율이 요동친다. 2건 중 1건 실패는 50%지만 경보할 일이 아니다 —
     * 봇 하나가 404를 맞은 새벽마다 깨우는 알림은 곧 무시된다.
     */
    @Test
    @DisplayName("요청이 적으면 오류율이 높아도 경보하지 않는다")
    void errorRateNeedsEnoughSamples() {
        HealthSnapshot snapshot = new HealthSnapshot(2, 1, 0, 120, 400, 2, 10, 0, 50_000_000_000L, 0, 0, 0, 0);

        assertThat(snapshot.errorRate()).isEqualTo(0.5);
        assertThat(snapshot.breaches(LIMITS)).isEmpty();
    }

    /** 다만 5xx는 다르다. 사용자가 아니라 서버가 잘못한 것이라 한 건도 묻히면 안 된다. */
    @Test
    @DisplayName("5xx는 요청이 한 건뿐이어도 남긴다")
    void serverErrorIsReportedRegardlessOfSampleSize() {
        HealthSnapshot snapshot = new HealthSnapshot(1, 1, 1, 120, 400, 2, 10, 0, 50_000_000_000L, 0, 0, 0, 0);

        assertThat(snapshot.breaches(LIMITS)).anyMatch(line -> line.startsWith("5xx 1건"));
    }

    @Test
    @DisplayName("응답이 느려지면 최대 지연도 함께 남긴다")
    void slowResponsesAreReported() {
        HealthSnapshot snapshot = new HealthSnapshot(100, 0, 0, 2500, 9000, 2, 10, 0, 50_000_000_000L, 0, 0, 0, 0);

        assertThat(snapshot.breaches(LIMITS)).anyMatch(line -> line.contains("2500ms") && line.contains("9000ms"));
    }

    @Test
    @DisplayName("커넥션 풀이 차오르면 대기 스레드 수까지 남긴다")
    void poolPressureIsReported() {
        HealthSnapshot snapshot = new HealthSnapshot(100, 0, 0, 120, 400, 9, 10, 3, 50_000_000_000L, 0, 0, 0, 0);

        assertThat(snapshot.poolUsage()).isEqualTo(0.9);
        assertThat(snapshot.breaches(LIMITS)).anyMatch(line -> line.contains("9/10") && line.contains("대기 3"));
    }

    @Test
    @DisplayName("디스크 여유가 기준 아래로 내려가면 남긴다")
    void lowDiskIsReported() {
        HealthSnapshot snapshot = new HealthSnapshot(100, 0, 0, 120, 400, 2, 10, 0, 100_000_000L, 0, 0, 0, 0);

        assertThat(snapshot.breaches(LIMITS)).anyMatch(line -> line.startsWith("디스크 여유"));
    }

    @Test
    @DisplayName("디스크가 진짜로 가득 찼으면(0바이트) '측정 불가'로 오인하지 않고 남긴다")
    void diskCompletelyFullIsReported() {
        HealthSnapshot snapshot = new HealthSnapshot(100, 0, 0, 120, 400, 2, 10, 0, 0L, 0, 0, 0, 0);

        assertThat(snapshot.breaches(LIMITS)).anyMatch(line -> line.startsWith("디스크 여유"));
    }

    @Test
    @DisplayName("디스크 측정이 실패했으면(-1) 경보하지 않는다")
    void diskMeasurementUnavailableIsNotReported() {
        HealthSnapshot snapshot = new HealthSnapshot(100, 0, 0, 120, 400, 2, 10, 0, -1L, 0, 0, 0, 0);

        assertThat(snapshot.breaches(LIMITS)).isEmpty();
    }

    @Test
    @DisplayName("메일이 나가지 않고 쌓이면 대기와 포기를 구분해 남긴다")
    void mailBacklogIsReported() {
        HealthSnapshot snapshot = new HealthSnapshot(100, 0, 0, 120, 400, 2, 10, 0, 50_000_000_000L, 50, 3, 0, 0);

        assertThat(snapshot.breaches(LIMITS))
                .anyMatch(line -> line.startsWith("발송 대기 메일 50통"))
                .anyMatch(line -> line.startsWith("발송 포기 메일 3통"));
    }

    /**
     * 이 항목만 성격이 다르다. 앱은 멀쩡한데 사람이 신고를 보고 있지 않다는 뜻이고,
     * 그동안 신고된 글은 그대로 보인다.
     */
    @Test
    @DisplayName("미처리 신고가 쌓이면 남긴다 — 앱이 아니라 사람이 멈춘 신호다")
    void pendingReportBacklogIsReported() {
        HealthSnapshot snapshot = new HealthSnapshot(100, 0, 0, 120, 400, 2, 10, 0, 50_000_000_000L, 0, 0, 35, 0);

        assertThat(snapshot.breaches(LIMITS)).anyMatch(line -> line.startsWith("미처리 신고 35건"));
        assertThat(snapshot.summary()).contains("미처리신고=35");
    }

    /** 한 번에 여러 곳이 무너지는 게 실제 장애다. 로그 한 줄에 전부 적혀야 판단할 수 있다. */
    @Test
    @DisplayName("여러 항목이 동시에 무너지면 전부 한 줄에 담는다")
    void allBreachesAreCollected() {
        HealthSnapshot snapshot = new HealthSnapshot(100, 50, 5, 3000, 9000, 10, 10, 7, 1_000_000L, 99, 9, 30, 0);

        assertThat(snapshot.breaches(LIMITS)).hasSize(8);
    }

    @Test
    @DisplayName("트래픽이 전혀 없는 주기는 0으로 나누지 않는다")
    void idlePeriodIsSafe() {
        HealthSnapshot idle = new HealthSnapshot(0, 0, 0, 0, 0, 0, 0, 0, 50_000_000_000L, 0, 0, 0, 0);

        assertThat(idle.errorRate()).isZero();
        assertThat(idle.poolUsage()).isZero();
        assertThat(idle.breaches(LIMITS)).isEmpty();
        assertThat(idle.summary()).contains("요청=0");
    }
}
