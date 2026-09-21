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
 * @param reportsPending   처리하지 않은 신고 수 상한. 이것은 앱이 아니라 <b>사람이 멈춘</b>
 *                         신호다 — 기계가 대신 처리할 수 없으므로, 쌓이고 있다는 사실 자체를
 *                         알려 주는 것이 전부다.
 * @param slowRequests     고정 임계값(ms)을 넘은 요청 수 상한(O05). 평균이 정상 범위여도 이
 *                         값이 늘면 일부 요청만 유독 느려지고 있다는 뜻이다.
 * @param sessionRevocationFailed 재시도를 모두 소진한 세션 폐기 태스크 수 상한(O03). 이건
 *                         사람이 봐야 낫는다 — mailFailed와 같은 성격이다.
 * @param imageDeleteBacklog 삭제 예약됐지만 아직 못 지운 이미지 파일 수 상한(O03). 정리
 *                         주기가 막혔거나 계속 실패하고 있다는 신호다.
 * @param recommendationHistoryStaleHours 추천 이력 검증 기준이 갱신되지 않은 채 지날 수
 *                         있는 최대 시간(O03). 자동 수집이 꺼져 있거나 매주 실패하고 있다는
 *                         신호다.
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
        long reportsPending,
        long slowRequests,
        long sessionRevocationFailed,
        long imageDeleteBacklog,
        long recommendationHistoryStaleHours) {
}
