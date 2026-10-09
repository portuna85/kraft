package com.kraft.shared.web;

import com.kraft.shared.security.OwnershipPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 게시글·댓글·업로드 작성과 검색에 계정(또는 IP) 기준 속도 제한을 건다. {@link FixedWindowRateLimiter}를 쓰고, 값은 재배포 없이
 * 조정하도록 {@code app.write.rate-limit.*}로 뺐다(실측 전 초기값 — {@link #reportAndCleanup()}의 집계가 근거). 관리자는
 * 제외한다(공지 작성 같은 운영 작업이 걸리면 안 된다).
 */
@Slf4j
@Component
public class WriteRateLimiters {

    private static final long MINUTE_MILLIS = 60_000L;
    private static final long HOUR_MILLIS = 3_600_000L;

    private final boolean enabled;
    private final FixedWindowRateLimiter postPerMinute;
    private final FixedWindowRateLimiter postPerHour;
    private final FixedWindowRateLimiter comment;
    private final FixedWindowRateLimiter upload;
    private final FixedWindowRateLimiter search;

    public WriteRateLimiters(
            @Value("${app.write.rate-limit.enabled:true}") boolean enabled,
            @Value("${app.write.rate-limit.post-per-minute:3}") int postPerMinuteLimit,
            @Value("${app.write.rate-limit.post-per-hour:20}") int postPerHourLimit,
            @Value("${app.write.rate-limit.comment-per-minute:10}") int commentPerMinuteLimit,
            @Value("${app.write.rate-limit.upload-per-minute:10}") int uploadPerMinuteLimit,
            @Value("${app.write.rate-limit.search-per-minute:60}") int searchPerMinuteLimit) {
        this.enabled = enabled;
        this.postPerMinute = new FixedWindowRateLimiter("게시글(분당)", postPerMinuteLimit, MINUTE_MILLIS);
        this.postPerHour = new FixedWindowRateLimiter("게시글(시간당)", postPerHourLimit, HOUR_MILLIS);
        this.comment = new FixedWindowRateLimiter("댓글", commentPerMinuteLimit, MINUTE_MILLIS);
        this.upload = new FixedWindowRateLimiter("업로드", uploadPerMinuteLimit, MINUTE_MILLIS);
        this.search = new FixedWindowRateLimiter("검색", searchPerMinuteLimit, MINUTE_MILLIS);
    }

    /** 게시글 작성. 분당·시간당을 항상 둘 다 검사한다(분당에 걸린 시도도 시간당 예산을 소모해, 빠른 반복이 시간당 한도로도 더 빨리 막힌다). */
    public boolean tryAcquirePost(Authentication authentication) {
        if (!enabled || OwnershipPolicy.isAdmin(authentication)) {
            return true;
        }
        String key = authentication.getName();
        boolean minuteOk = postPerMinute.tryAcquire(key);
        boolean hourOk = postPerHour.tryAcquire(key);
        return minuteOk && hourOk;
    }

    public boolean tryAcquireComment(Authentication authentication) {
        return isAdminOrDisabled(authentication) || comment.tryAcquire(authentication.getName());
    }

    public boolean tryAcquireUpload(Authentication authentication) {
        return isAdminOrDisabled(authentication) || upload.tryAcquire(authentication.getName());
    }

    /** IP 기준이다 — 검색은 로그인 없이도 할 수 있어 계정 키가 없을 수 있다. */
    public boolean tryAcquireSearch(String clientIp) {
        return !enabled || search.tryAcquire(clientIp);
    }

    private boolean isAdminOrDisabled(Authentication authentication) {
        return !enabled || OwnershipPolicy.isAdmin(authentication);
    }

    /** 다섯 제한기의 허용/거부 집계를 주기적으로 남긴다. */
    @Scheduled(fixedDelayString = "${app.write.rate-limit.report-interval-ms:600000}")
    public void reportAndCleanup() {
        long now = Instant.now().toEpochMilli();
        postPerMinute.reportAndCleanup(now);
        postPerHour.reportAndCleanup(now);
        comment.reportAndCleanup(now);
        upload.reportAndCleanup(now);
        search.reportAndCleanup(now);
    }
}
