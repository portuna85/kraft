package com.kraft.shared.web;

import com.kraft.user.domain.Role;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 게시글·댓글·신고·업로드 작성과 검색에 계정(또는 IP) 기준 속도 제한을 건다(전체 리뷰
 * 2026-09-26 A-SEC-06). 로그인·가입 등은 이미 {@code AuthRateLimitFilter}가 지키지만,
 * 인증을 마친 계정은 이 제한이 생기기 전까지 무제한으로 빠르게 쓸 수 있었다 — 이메일 인증만
 * 통과하면 스팸 봇 하나로 게시판 전체를 덮을 수 있었다.
 * <p>
 * {@link RecommendationRateLimiter}·{@code AuthRateLimitFilter}와 같은
 * {@link FixedWindowRateLimiter}를 재사용하고, 값은 재배포 없이 조정할 수 있게
 * {@code app.write.rate-limit.*}로 뺐다 — 실측 전 초기값이며,
 * {@link #reportAndCleanup()}이 남기는 허용/거부 집계가 조정 근거가 된다.
 * <p>
 * 관리자는 제외한다 — 신고 처리·공지 작성 같은 운영 작업이 이 제한에 걸리면 안 된다.
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
    private final FixedWindowRateLimiter report;
    private final FixedWindowRateLimiter upload;
    private final FixedWindowRateLimiter search;

    public WriteRateLimiters(
            @Value("${app.write.rate-limit.enabled:true}") boolean enabled,
            @Value("${app.write.rate-limit.post-per-minute:3}") int postPerMinuteLimit,
            @Value("${app.write.rate-limit.post-per-hour:20}") int postPerHourLimit,
            @Value("${app.write.rate-limit.comment-per-minute:10}") int commentPerMinuteLimit,
            @Value("${app.write.rate-limit.report-per-minute:5}") int reportPerMinuteLimit,
            @Value("${app.write.rate-limit.upload-per-minute:10}") int uploadPerMinuteLimit,
            @Value("${app.write.rate-limit.search-per-minute:60}") int searchPerMinuteLimit) {
        this.enabled = enabled;
        this.postPerMinute = new FixedWindowRateLimiter("게시글(분당)", postPerMinuteLimit, MINUTE_MILLIS);
        this.postPerHour = new FixedWindowRateLimiter("게시글(시간당)", postPerHourLimit, HOUR_MILLIS);
        this.comment = new FixedWindowRateLimiter("댓글", commentPerMinuteLimit, MINUTE_MILLIS);
        this.report = new FixedWindowRateLimiter("신고", reportPerMinuteLimit, MINUTE_MILLIS);
        this.upload = new FixedWindowRateLimiter("업로드", uploadPerMinuteLimit, MINUTE_MILLIS);
        this.search = new FixedWindowRateLimiter("검색", searchPerMinuteLimit, MINUTE_MILLIS);
    }

    /**
     * 게시글 작성. 분당·시간당 두 창을 함께 검사한다 — 순서와 무관하게 항상 둘 다
     * {@code tryAcquire}를 호출한다(카운트 자체는 시도마다 늘어야 하므로, 분당 제한에 이미
     * 걸린 시도도 시간당 예산을 함께 소모한다 — 빠르게 반복하는 시도가 시간당 한도로도
     * 더 빨리 막히게 하려는 의도다).
     */
    public boolean tryAcquirePost(Authentication authentication) {
        if (!enabled || isAdmin(authentication)) {
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

    public boolean tryAcquireReport(Authentication authentication) {
        return isAdminOrDisabled(authentication) || report.tryAcquire(authentication.getName());
    }

    public boolean tryAcquireUpload(Authentication authentication) {
        return isAdminOrDisabled(authentication) || upload.tryAcquire(authentication.getName());
    }

    /** IP 기준이다 — 검색은 로그인 없이도 할 수 있어 계정 키가 없을 수 있다. */
    public boolean tryAcquireSearch(String clientIp) {
        return !enabled || search.tryAcquire(clientIp);
    }

    private boolean isAdminOrDisabled(Authentication authentication) {
        return !enabled || isAdmin(authentication);
    }

    private static boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(Role.ADMIN.getKey()));
    }

    /** 실측 없이는 한도가 맞는지 알 수 없다 — 여섯 제한기의 허용/거부 집계를 주기적으로 남긴다. */
    @Scheduled(fixedDelayString = "${app.write.rate-limit.report-interval-ms:600000}")
    public void reportAndCleanup() {
        long now = Instant.now().toEpochMilli();
        postPerMinute.reportAndCleanup(now);
        postPerHour.reportAndCleanup(now);
        comment.reportAndCleanup(now);
        report.reportAndCleanup(now);
        upload.reportAndCleanup(now);
        search.reportAndCleanup(now);
    }
}
