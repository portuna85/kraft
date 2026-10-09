package com.kraft.observability;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 한 주기의 상태 값. {@link #breaches}는 순수 함수라 DB·시계 없이 임계 판정만 검증할 수 있다.
 * <p>
 * {@code -1}은 "측정 불가"다. {@code diskFreeBytes}에서는 진짜 0바이트(가득 참)와 섞이지 않게, DB 집계
 * 필드(mailPending·mailFailed·sessionRevocationFailed·imageDeleteBacklog)에서는 조회 실패가 "이상 없음"으로
 * 보이지 않게 {@link #breaches}가 직접 남긴다. {@code recommendationHistoryAgeHours}의 -1은 이력이 없거나
 * 검증된 적이 없다는 뜻이며 {@code recommendEnabled}일 때만 "미준비"로 남긴다.
 *
 * @param requests     정적 자원을 뺀 요청 수
 * @param poolTotal    풀 크기. 알 수 없으면 0
 * @param slowRequests 느린 요청 기준(RequestMetrics.slowThresholdMillis)을 넘은 수 — 평균에 묻히는 소수를 직접 센다
 */
public record HealthSnapshot(
        long requests,
        long errors,
        long serverErrors,
        long avgMillis,
        long maxMillis,
        int poolActive,
        int poolTotal,
        int poolPending,
        long diskFreeBytes,
        long mailPending,
        long mailFailed,
        long slowRequests,
        long sessionRevocationFailed,
        long imageDeleteBacklog,
        long recommendationHistoryAgeHours,
        boolean recommendEnabled,
        int recommendationFetchFailures) {

    /** 이만큼 연속으로 수집이 실패하면(주 4회 시도 = 한 주 내내) 사람이 봐야 한다. */
    static final int RECOMMENDATION_FETCH_FAILURE_LIMIT = 4;

    /** 수집 연속 실패 수를 모르는(측정하지 않는) 호출용. */
    public HealthSnapshot(long requests, long errors, long serverErrors, long avgMillis, long maxMillis,
                          int poolActive, int poolTotal, int poolPending, long diskFreeBytes,
                          long mailPending, long mailFailed, long slowRequests,
                          long sessionRevocationFailed, long imageDeleteBacklog,
                          long recommendationHistoryAgeHours, boolean recommendEnabled) {
        this(requests, errors, serverErrors, avgMillis, maxMillis, poolActive, poolTotal, poolPending,
                diskFreeBytes, mailPending, mailFailed, slowRequests, sessionRevocationFailed,
                imageDeleteBacklog, recommendationHistoryAgeHours, recommendEnabled, 0);
    }

    public double errorRate() {
        return requests == 0 ? 0 : (double) errors / requests;
    }

    public double poolUsage() {
        return poolTotal == 0 ? 0 : (double) poolActive / poolTotal;
    }

    /** 기준을 넘긴 항목을 사람이 읽을 문장으로 돌려준다(비어 있으면 정상). 이 목록이 그대로 ERROR 한 줄이 된다. */
    public List<String> breaches(HealthThresholds limits) {
        return breachList(limits).stream().map(Breach::message).toList();
    }

    /** {@link #breaches}와 같은 판정의 항목별 안정된 식별자. 메시지는 수치가 달라 {@link AlertMailer}의 억제 키로 못 쓴다. */
    Set<String> breachKinds(HealthThresholds limits) {
        return breachList(limits).stream().map(Breach::kind).collect(Collectors.toSet());
    }

    private record Breach(String kind, String message) {
    }

    private List<Breach> breachList(HealthThresholds limits) {
        List<Breach> found = new ArrayList<>();

        // 오류율은 표본이 충분할 때만 보고, 5xx는 한 건이라도 센다.
        if (requests >= limits.minRequests() && errorRate() > limits.errorRate()) {
            found.add(new Breach("ERROR_RATE", "HTTP 오류율 %.1f%% (기준 %.1f%%, 요청 %d건)"
                    .formatted(errorRate() * 100, limits.errorRate() * 100, requests)));
        }
        if (serverErrors > limits.serverErrors()) {
            found.add(new Breach("SERVER_ERRORS", "5xx %d건 (기준 %d건)".formatted(serverErrors, limits.serverErrors())));
        }
        if (requests >= limits.minRequests() && avgMillis > limits.avgMillis()) {
            found.add(new Breach("AVG_RESPONSE",
                    "평균 응답 %dms (기준 %dms, 최대 %dms)".formatted(avgMillis, limits.avgMillis(), maxMillis)));
        }
        if (poolTotal > 0 && poolUsage() > limits.poolUsage()) {
            found.add(new Breach("POOL_USAGE", "DB 커넥션 %d/%d 사용 중, 대기 %d (기준 %.0f%%)"
                    .formatted(poolActive, poolTotal, poolPending, limits.poolUsage() * 100)));
        }
        if (diskFreeBytes >= 0 && diskFreeBytes < limits.diskFreeBytes()) {
            found.add(new Breach("DISK_FREE", "디스크 여유 %dMB (기준 %dMB)"
                    .formatted(diskFreeBytes / 1048576, limits.diskFreeBytes() / 1048576)));
        }
        if (mailPending == -1) {
            found.add(new Breach("MAIL_PENDING", "측정 불가: 발송 대기 메일 수"));
        } else if (mailPending > limits.mailPending()) {
            found.add(new Breach("MAIL_PENDING", "발송 대기 메일 %d통 (기준 %d통)".formatted(mailPending, limits.mailPending())));
        }
        if (mailFailed == -1) {
            found.add(new Breach("MAIL_FAILED", "측정 불가: 발송 포기 메일 수"));
        } else if (mailFailed > limits.mailFailed()) {
            found.add(new Breach("MAIL_FAILED", "발송 포기 메일 %d통 (기준 %d통)".formatted(mailFailed, limits.mailFailed())));
        }
        if (slowRequests > limits.slowRequests()) {
            found.add(new Breach("SLOW_REQUESTS",
                    "느린 요청 %d건 (기준 %d건, 최대 %dms)".formatted(slowRequests, limits.slowRequests(), maxMillis)));
        }
        if (sessionRevocationFailed == -1) {
            found.add(new Breach("SESSION_REVOCATION_FAILED", "측정 불가: 세션 폐기 실패 수"));
        } else if (sessionRevocationFailed > limits.sessionRevocationFailed()) {
            found.add(new Breach("SESSION_REVOCATION_FAILED",
                    "세션 폐기 실패 %d건 (기준 %d건)".formatted(sessionRevocationFailed, limits.sessionRevocationFailed())));
        }
        if (imageDeleteBacklog == -1) {
            found.add(new Breach("IMAGE_DELETE_BACKLOG", "측정 불가: 이미지 삭제 backlog"));
        } else if (imageDeleteBacklog > limits.imageDeleteBacklog()) {
            found.add(new Breach("IMAGE_DELETE_BACKLOG",
                    "이미지 삭제 backlog %d건 (기준 %d건)".formatted(imageDeleteBacklog, limits.imageDeleteBacklog())));
        }
        if (recommendationHistoryAgeHours == -1) {
            // 기능이 꺼져 있으면 이력이 없는 게 정상이다.
            if (recommendEnabled) {
                found.add(new Breach("RECOMMENDATION_HISTORY_STALE", "추천 이력 미준비"));
            }
        } else if (recommendationHistoryAgeHours > limits.recommendationHistoryStaleHours()) {
            found.add(new Breach("RECOMMENDATION_HISTORY_STALE", "추천 이력 검증 기준이 %d시간째 갱신되지 않음 (기준 %d시간)"
                    .formatted(recommendationHistoryAgeHours, limits.recommendationHistoryStaleHours())));
        }
        if (recommendEnabled && recommendationFetchFailures >= RECOMMENDATION_FETCH_FAILURE_LIMIT) {
            found.add(new Breach("RECOMMENDATION_FETCH_FAILING",
                    "추천 이력 자동 수집이 %d회 연속 실패 (기준 %d회)"
                            .formatted(recommendationFetchFailures, RECOMMENDATION_FETCH_FAILURE_LIMIT)));
        }
        return found;
    }

    /** 주기마다 남기는 한 줄. 넘긴 항목이 없어도 이 줄은 남아 평소 수치를 알 수 있게 한다. */
    public String summary() {
        return ("요청=%d 오류=%d(%.1f%%) 5xx=%d 평균=%dms 최대=%dms 느린요청=%d "
                + "DB풀=%d/%d 대기=%d 디스크여유=%s 메일대기=%d 메일실패=%d "
                + "세션폐기실패=%d 이미지삭제backlog=%d 추천이력나이=%d시간")
                .formatted(requests, errors, errorRate() * 100, serverErrors, avgMillis, maxMillis, slowRequests,
                        poolActive, poolTotal, poolPending, diskFreeSummary(), mailPending, mailFailed,
                        sessionRevocationFailed, imageDeleteBacklog, recommendationHistoryAgeHours);
    }

    /** -1(측정 불가)은 "측정불가"로 — 정수 나눗셈이 0MB로 내려 진짜 0바이트와 섞이지 않게. */
    private String diskFreeSummary() {
        return diskFreeBytes == -1 ? "측정불가" : (diskFreeBytes / 1048576) + "MB";
    }
}
