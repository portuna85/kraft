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
 * @param mailPending   발송 대기 중인 메일
 * @param mailFailed    재시도를 모두 소진한 메일
 * @param reportsPending 관리자가 아직 처리하지 않은 신고
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
        long reportsPending) {

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
        if (mailPending > limits.mailPending()) {
            found.add("발송 대기 메일 %d통 (기준 %d통)".formatted(mailPending, limits.mailPending()));
        }
        if (mailFailed > limits.mailFailed()) {
            found.add("발송 포기 메일 %d통 (기준 %d통)".formatted(mailFailed, limits.mailFailed()));
        }
        // 다른 항목과 성격이 다르다. 앱은 멀쩡한데 사람이 보고 있지 않다는 뜻이고, 그동안
        // 신고된 글은 그대로 보인다.
        if (reportsPending > limits.reportsPending()) {
            found.add("미처리 신고 %d건 (기준 %d건)".formatted(reportsPending, limits.reportsPending()));
        }
        return found;
    }

    /** 주기마다 남기는 한 줄. 넘긴 항목이 없어도 이 줄은 남아 평소 수치를 알 수 있게 한다. */
    public String summary() {
        return ("요청=%d 오류=%d(%.1f%%) 5xx=%d 평균=%dms 최대=%dms "
                + "DB풀=%d/%d 대기=%d 디스크여유=%dMB 메일대기=%d 메일실패=%d 미처리신고=%d")
                .formatted(requests, errors, errorRate() * 100, serverErrors, avgMillis, maxMillis,
                        poolActive, poolTotal, poolPending, diskFreeBytes / 1048576, mailPending, mailFailed,
                        reportsPending);
    }
}
