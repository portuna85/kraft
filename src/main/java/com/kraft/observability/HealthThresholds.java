package com.kraft.observability;

/**
 * 어디부터를 "이상"으로 볼지에 대한 기준. {@code app.metrics.*}로 조정한다.
 *
 * @param minRequests      오류율을 판정하기 위해 필요한 최소 요청 수. 3건 중 1건 실패를 33%로
 *                         읽으면 한밤중에 봇 하나가 404를 맞을 때마다 경보가 울린다.
 * @param errorRate        이 비율(0~1)을 넘는 4xx+5xx
 * @param serverErrors     한 주기의 5xx 허용치. 5xx는 비율과 무관하게 한 건만 나도 남길 값이라
 *                         보통 0~1로 둔다.
 * @param avgMillis        평균 응답 시간 상한
 * @param poolUsage        DB 커넥션 풀 사용률(0~1) 상한
 * @param diskFreeBytes    업로드 디렉터리가 있는 디스크의 여유 공간 하한
 * @param mailPending      아직 보내지 못한 메일 수 상한. 주기 작업이 도는데도 줄지 않으면 SMTP가
 *                         죽어 있는 것이다.
 * @param mailFailed       재시도를 모두 소진한 메일 수 상한. 이건 사람이 봐야 낫는다.
 */
public record HealthThresholds(
        int minRequests,
        double errorRate,
        long serverErrors,
        long avgMillis,
        double poolUsage,
        long diskFreeBytes,
        long mailPending,
        long mailFailed) {
}
