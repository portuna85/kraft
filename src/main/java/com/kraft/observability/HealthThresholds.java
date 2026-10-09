package com.kraft.observability;

/**
 * 어디부터를 "이상"으로 볼지의 기준({@code app.metrics.*}로 조정).
 *
 * @param minRequests   오류율 판정에 필요한 최소 요청 수(3건 중 1건 실패를 33%로 읽지 않게)
 * @param errorRate     이 비율(0~1)을 넘는 4xx+5xx
 * @param serverErrors  한 주기의 5xx 허용치(보통 0~1)
 * @param poolUsage     DB 커넥션 풀 사용률(0~1) 상한
 * @param diskFreeBytes 업로드 디렉터리 디스크의 여유 공간 하한
 * @param mailPending   미발송 메일 수 상한 — 주기 작업이 도는데 줄지 않으면 SMTP가 죽은 것이다
 * @param mailFailed    재시도를 소진한 메일 수 상한 — 사람이 봐야 낫는다
 * @param slowRequests  느린 요청(RequestMetrics 기준) 수 상한 — 평균이 정상이어도 일부만 느린 것을 잡는다
 * @param sessionRevocationFailed 재시도를 소진한 세션 폐기 태스크 수 상한(사람이 봐야 한다)
 * @param imageDeleteBacklog 삭제 예약됐지만 못 지운 이미지 수 상한 — 정리가 막혔다는 신호
 * @param recommendationHistoryStaleHours 추천 이력 검증 기준이 갱신 없이 지날 수 있는 최대 시간 — 자동 수집이 꺼졌거나 실패 중이라는 신호
 */
public record HealthThresholds(
        int minRequests,
        double errorRate,
        long serverErrors,
        long avgMillis,
        double poolUsage,
        long diskFreeBytes,
        long mailPending,
        long mailFailed,
        long slowRequests,
        long sessionRevocationFailed,
        long imageDeleteBacklog,
        long recommendationHistoryStaleHours) {
}
