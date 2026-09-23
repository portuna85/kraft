package com.kraft.observability;

import java.util.ArrayList;
import java.util.List;

/**
 * 한 주기의 상태를 담은 값. 만드는 것(수집)과 판정하는 것을 분리해 두었다 —
 * {@link #breaches}가 순수 함수라 DB나 시계 없이 임계 판정만 따로 검증할 수 있다.
 *
 * @param requests      정적 자원을 뺀 요청 수
 * @param poolActive    지금 쓰고 있는 DB 커넥션 수
 * @param poolTotal     풀 크기. 알 수 없으면 0
 * @param poolPending   커넥션을 기다리는 스레드 수
 * @param diskFreeBytes 업로드 디렉터리 쪽 여유 공간. {@code -1}은 측정 자체가 불가능했다는
 *                      뜻이고, {@code 0}은 실제로 디스크가 가득 찼다는 뜻이다 — 둘을 같은 값으로
 *                      두면 가장 위험한 "진짜 0바이트" 상태가 "측정 불가"로 오인되어 경보 대상에서
 *                      빠진다
 * @param mailPending   발송 대기 중인 메일. {@code -1}은 이번 주기의 DB 집계 조회 자체가
 *                      실패해 측정하지 못했다는 것이다 — {@link #breaches}가 "측정 불가"로
 *                      직접 남긴다(개선 보고서 OBS-01). 예전에는 이 -1이 임계값 비교만 건너뛰고
 *                      끝나, 관측 자체가 실패한 주기가 "이상 없음"과 로그상 구분되지 않았다.
 * @param mailFailed    재시도를 모두 소진한 메일. {@code -1}의 뜻은 mailPending과 같다
 * @param reportsPending 관리자가 아직 처리하지 않은 신고. {@code -1}의 뜻은 mailPending과 같다
 * @param slowRequests  고정 임계값(RequestMetrics의 slowThresholdMillis, 기본 3000ms)을 넘은
 *                      요청 수(O05). 평균·최댓값만으로는 소수의 느린 요청이 다수의 빠른 요청에
 *                      묻힌다 — 이 값은 그 소수를 직접 센다.
 * @param sessionRevocationFailed 재시도를 모두 소진해 사람이 봐야 하는 세션 폐기 태스크
 *                      수(O03). {@code -1}의 뜻은 mailPending과 같다.
 * @param imageDeleteBacklog 삭제 예약됐지만 아직 실제로 지우지 못한 이미지 파일 수(O03).
 *                      {@code -1}의 뜻은 mailPending과 같다.
 * @param recommendationHistoryAgeHours 추천 이력 검증 기준(verifiedAt)이 마지막으로 갱신된
 *                      지 지난 시간(O03). 상태 행이 없거나 한 번도 검증되지 않았으면 {@code -1}.
 *                      {@code recommendEnabled}가 참이면 이 -1은 "측정 실패"가 아니라
 *                      "이력이 아직 준비되지 않음"을 뜻하므로 {@link #breaches}가 별도 문구로
 *                      남긴다 — 기능이 꺼져 있으면(false) 애초에 이력이 없는 게 정상이라 남기지
 *                      않는다.
 * @param recommendEnabled {@code app.recommend.enabled}. recommendationHistoryAgeHours의
 *                      -1을 "정상(기능 꺼짐)"과 "미준비(기능 켜짐)"로 구분하는 데만 쓴다(OBS-01).
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
        long reportsPending,
        long slowRequests,
        long sessionRevocationFailed,
        long imageDeleteBacklog,
        long recommendationHistoryAgeHours,
        boolean recommendEnabled) {

    public double errorRate() {
        return requests == 0 ? 0 : (double) errors / requests;
    }

    public double poolUsage() {
        return poolTotal == 0 ? 0 : (double) poolActive / poolTotal;
    }

    /**
     * 기준을 넘긴 항목을 사람이 읽을 문장으로 돌려준다. 비어 있으면 정상이다.
     * <p>
     * 넘긴 항목만 담는 이유는 이 목록이 그대로 ERROR 한 줄이 되기 때문이다. 무엇이 잘못됐는지
     * 로그 한 줄에 다 적혀 있어야 새벽에 깨어나서도 판단할 수 있다.
     * <p>
     * DB 집계가 {@code -1}(측정 불가)이면 임계값 비교를 건너뛰는 대신 "측정 불가: <항목>"을
     * 직접 남긴다(개선 보고서 OBS-01) — 예전에는 조용히 건너뛰어, DB 집계 자체가 계속
     * 실패하는 주기가 로그상 "이상 없음"과 구분되지 않았다. 관측이 실패했다는 것 자체가
     * 알아야 할 상태다.
     */
    public List<String> breaches(HealthThresholds limits) {
        List<String> found = new ArrayList<>();

        // 표본이 적으면 비율이 요동치므로 오류율은 요청이 충분히 모였을 때만 본다.
        // 반면 5xx는 표본과 무관하게 센다 — 한 건이라도 서버가 잘못한 것이기 때문이다.
        if (requests >= limits.minRequests() && errorRate() > limits.errorRate()) {
            found.add("HTTP 오류율 %.1f%% (기준 %.1f%%, 요청 %d건)"
                    .formatted(errorRate() * 100, limits.errorRate() * 100, requests));
        }
        if (serverErrors > limits.serverErrors()) {
            found.add("5xx %d건 (기준 %d건)".formatted(serverErrors, limits.serverErrors()));
        }
        if (requests >= limits.minRequests() && avgMillis > limits.avgMillis()) {
            found.add("평균 응답 %dms (기준 %dms, 최대 %dms)".formatted(avgMillis, limits.avgMillis(), maxMillis));
        }
        if (poolTotal > 0 && poolUsage() > limits.poolUsage()) {
            found.add("DB 커넥션 %d/%d 사용 중, 대기 %d (기준 %.0f%%)"
                    .formatted(poolActive, poolTotal, poolPending, limits.poolUsage() * 100));
        }
        if (diskFreeBytes >= 0 && diskFreeBytes < limits.diskFreeBytes()) {
            found.add("디스크 여유 %dMB (기준 %dMB)"
                    .formatted(diskFreeBytes / 1048576, limits.diskFreeBytes() / 1048576));
        }
        if (mailPending == -1) {
            found.add("측정 불가: 발송 대기 메일 수");
        } else if (mailPending > limits.mailPending()) {
            found.add("발송 대기 메일 %d통 (기준 %d통)".formatted(mailPending, limits.mailPending()));
        }
        if (mailFailed == -1) {
            found.add("측정 불가: 발송 포기 메일 수");
        } else if (mailFailed > limits.mailFailed()) {
            found.add("발송 포기 메일 %d통 (기준 %d통)".formatted(mailFailed, limits.mailFailed()));
        }
        // 다른 항목과 성격이 다르다. 앱은 멀쩡한데 사람이 보고 있지 않다는 뜻이고, 그동안
        // 신고된 글은 그대로 보인다.
        if (reportsPending == -1) {
            found.add("측정 불가: 미처리 신고 수");
        } else if (reportsPending > limits.reportsPending()) {
            found.add("미처리 신고 %d건 (기준 %d건)".formatted(reportsPending, limits.reportsPending()));
        }
        if (slowRequests > limits.slowRequests()) {
            found.add("느린 요청 %d건 (기준 %d건, 최대 %dms)".formatted(slowRequests, limits.slowRequests(), maxMillis));
        }
        if (sessionRevocationFailed == -1) {
            found.add("측정 불가: 세션 폐기 실패 수");
        } else if (sessionRevocationFailed > limits.sessionRevocationFailed()) {
            found.add("세션 폐기 실패 %d건 (기준 %d건)".formatted(sessionRevocationFailed, limits.sessionRevocationFailed()));
        }
        if (imageDeleteBacklog == -1) {
            found.add("측정 불가: 이미지 삭제 backlog");
        } else if (imageDeleteBacklog > limits.imageDeleteBacklog()) {
            found.add("이미지 삭제 backlog %d건 (기준 %d건)".formatted(imageDeleteBacklog, limits.imageDeleteBacklog()));
        }
        if (recommendationHistoryAgeHours == -1) {
            // 기능이 꺼져 있으면 이력이 애초에 없는 게 정상이다 — 그때는 남기지 않는다.
            if (recommendEnabled) {
                found.add("추천 이력 미준비");
            }
        } else if (recommendationHistoryAgeHours > limits.recommendationHistoryStaleHours()) {
            found.add("추천 이력 검증 기준이 %d시간째 갱신되지 않음 (기준 %d시간)"
                    .formatted(recommendationHistoryAgeHours, limits.recommendationHistoryStaleHours()));
        }
        return found;
    }

    /** 주기마다 남기는 한 줄. 넘긴 항목이 없어도 이 줄은 남아 평소 수치를 알 수 있게 한다. */
    public String summary() {
        return ("요청=%d 오류=%d(%.1f%%) 5xx=%d 평균=%dms 최대=%dms 느린요청=%d "
                + "DB풀=%d/%d 대기=%d 디스크여유=%s 메일대기=%d 메일실패=%d 미처리신고=%d "
                + "세션폐기실패=%d 이미지삭제backlog=%d 추천이력나이=%d시간")
                .formatted(requests, errors, errorRate() * 100, serverErrors, avgMillis, maxMillis, slowRequests,
                        poolActive, poolTotal, poolPending, diskFreeSummary(), mailPending, mailFailed,
                        reportsPending, sessionRevocationFailed, imageDeleteBacklog, recommendationHistoryAgeHours);
    }

    /**
     * {@code -1}(측정 불가)을 {@code "측정불가"}로 보여준다(개선 보고서 OBS-01) — 정수 나눗셈이
     * {@code -1 / 1048576}을 0으로 내림해, 예전에는 측정 실패가 "디스크여유=0MB"로 찍혀 진짜
     * 0바이트(가득 참)와 로그상 구분되지 않았다.
     */
    private String diskFreeSummary() {
        return diskFreeBytes == -1 ? "측정불가" : (diskFreeBytes / 1048576) + "MB";
    }
}
