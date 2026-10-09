package com.kraft.post.web;

import com.kraft.shared.security.OwnershipPolicy;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 상세 화면 조회수를 올릴지 판단한다(새로고침·봇·링크 미리보기·재방문이 조회수를 부풀리지 않게). 로그인 사용자는
 * 세션에, 익명은 쿠키에 "최근 본 글"을 담는다 — 익명마다 세션을 만들면 방문마다 세션 행이 쌓인다. 카운트 여부만
 * 정하고 반영은 {@code PostService.findByIdForView}의 즉시 UPDATE가 맡는다.
 */
@Component
public class PostViewDedup {

    /** 이 시간 안에 같은 글을 다시 열면 세지 않는다. */
    static final Duration WINDOW = Duration.ofHours(1);

    /** 검색엔진·모니터링 봇·메신저 링크 미리보기로 흔히 보이는 User-Agent 조각. */
    private static final Pattern BOT_USER_AGENT = Pattern.compile("(?i)bot|crawler|spider|preview");

    private static final String SESSION_ATTRIBUTE = "kraft.viewedPosts";
    private static final String COOKIE_NAME = "kraft_viewed";
    /** 쿠키 하나의 크기가 무한정 커지지 않도록 최근 것만 남긴다. */
    private static final int MAX_COOKIE_ENTRIES = 50;
    private static final int COOKIE_MAX_AGE_SECONDS = (int) WINDOW.getSeconds();

    /** @return 이번 요청에서 조회수를 올려야 하면 true. 판단하며 세션·쿠키의 최근 방문 시각을 갱신한다(쿠키는 응답에 다시 쓴다). */
    public boolean shouldCount(HttpServletRequest request, HttpServletResponse response,
                                Long postId, Authentication authentication) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent != null && BOT_USER_AGENT.matcher(userAgent).find()) {
            return false;
        }
        return OwnershipPolicy.isAuthenticated(authentication)
                ? shouldCountViaSession(request, postId)
                : shouldCountViaCookie(request, response, postId);
    }

    @SuppressWarnings("unchecked")
    private boolean shouldCountViaSession(HttpServletRequest request, Long postId) {
        HttpSession session = request.getSession(true);
        Map<Long, Instant> viewed = (Map<Long, Instant>) session.getAttribute(SESSION_ATTRIBUTE);
        if (viewed == null) {
            viewed = new HashMap<>();
        }

        Instant now = Instant.now();
        boolean shouldCount = isStale(viewed.get(postId), now);
        if (shouldCount) {
            viewed.put(postId, now);
        }
        boolean pruned = viewed.entrySet().removeIf(entry -> isStale(entry.getValue(), now));
        // 맵이 바뀐 경우에만 다시 심는다(조회마다 쓰면 세션 UPDATE가 늘고, 제자리 변경만으로는 Spring Session이 변경을 감지하지 못할 수 있다).
        if (shouldCount || pruned) {
            session.setAttribute(SESSION_ATTRIBUTE, viewed);
        }
        return shouldCount;
    }

    private boolean shouldCountViaCookie(HttpServletRequest request, HttpServletResponse response, Long postId) {
        Map<Long, Instant> viewed = parseCookie(request);
        Instant now = Instant.now();
        boolean shouldCount = isStale(viewed.get(postId), now);
        if (shouldCount) {
            viewed.put(postId, now);
        }
        viewed.entrySet().removeIf(entry -> isStale(entry.getValue(), now));
        writeCookie(request, response, cap(viewed));
        return shouldCount;
    }

    private boolean isStale(Instant lastViewedAt, Instant now) {
        return lastViewedAt == null || Duration.between(lastViewedAt, now).compareTo(WINDOW) >= 0;
    }

    /** 오래된 것부터 버려 최근 {@link #MAX_COOKIE_ENTRIES}개만 남긴다. */
    private Map<Long, Instant> cap(Map<Long, Instant> viewed) {
        if (viewed.size() <= MAX_COOKIE_ENTRIES) {
            return viewed;
        }
        return viewed.entrySet().stream()
                .sorted(Map.Entry.<Long, Instant>comparingByValue().reversed())
                .limit(MAX_COOKIE_ENTRIES)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, HashMap::new));
    }

    /**
     * 쿠키 값: {@code postId:epochSeconds}를 {@code |}로 이은 문자열(쉼표는 Tomcat이 거절하지만 옛 형식은 읽는다).
     * 형식이나 시간 범위가 이상한 항목만 버린다.
     */
    private Map<Long, Instant> parseCookie(HttpServletRequest request) {
        Map<Long, Instant> viewed = new HashMap<>();
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return viewed;
        }
        for (Cookie cookie : cookies) {
            if (!COOKIE_NAME.equals(cookie.getName()) || cookie.getValue() == null) {
                continue;
            }
            for (String entry : cookie.getValue().split("[|,]")) {
                String[] parts = entry.split(":", 2);
                if (parts.length != 2) {
                    continue;
                }
                try {
                    viewed.put(Long.parseLong(parts[0]), Instant.ofEpochSecond(Long.parseLong(parts[1])));
                } catch (NumberFormatException | DateTimeException e) {
                    // 손상되거나 변조된 항목은 조용히 건너뛴다 — 쿠키는 클라이언트가 보낸 값이다.
                }
            }
        }
        return viewed;
    }

    private void writeCookie(HttpServletRequest request, HttpServletResponse response, Map<Long, Instant> viewed) {
        StringBuilder value = new StringBuilder();
        for (Map.Entry<Long, Instant> entry : viewed.entrySet()) {
            if (!value.isEmpty()) {
                value.append('|');
            }
            value.append(entry.getKey()).append(':').append(entry.getValue().getEpochSecond());
        }
        Cookie cookie = new Cookie(COOKIE_NAME, value.toString());
        cookie.setPath("/");
        cookie.setMaxAge(COOKIE_MAX_AGE_SECONDS);
        cookie.setHttpOnly(true);
        // 로컬 개발은 평문 HTTP라 Secure를 무조건 걸면 쿠키가 저장되지 않는다(운영은 프록시 뒤라 isSecure가 실제 프로토콜을 반영한다).
        cookie.setSecure(request.isSecure());
        response.addCookie(cookie);
    }
}
