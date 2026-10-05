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
 * 상세 화면 조회수를 올릴지 판단한다(전체 리뷰 2026-09-26 A-BE-04 1단계). 새로고침·봇·링크
 * 미리보기(메신저가 URL을 긁어 가는 요청)·같은 방문자의 재방문이 전부 조회수를 1씩 올리던
 * 것을 줄인다 — 인기글·조회순 정렬이 봇 트래픽에 좌우되지 않게 한다.
 * <p>
 * 로그인 사용자는 세션에, 익명 사용자는 쿠키에 "최근 본 글"을 담는다. 세션은 Spring Session
 * JDBC가 서버에 보관하지만, 익명 방문자마다 세션을 새로 만들면(현재 앱은 인증된 요청에만
 * 세션이 생긴다) 공개 게시판의 방문마다 세션 테이블에 행이 쌓인다 — 그래서 익명은 쿠키 하나로
 * 가볍게 처리한다.
 * <p>
 * 쓰기(버퍼링)는 이 클래스의 범위가 아니다 — 카운트 여부만 정하고, 실제 반영은 여전히
 * {@code PostService.findByIdForView}의 즉시 UPDATE가 맡는다.
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

    /**
     * @return 이번 요청에서 조회수를 올려야 하면 {@code true}. 판단하는 과정에서 세션·쿠키
     * 상태를 최신 방문 시각으로 갱신한다(응답에 쿠키를 다시 써야 할 수도 있어 {@code response}가 필요하다).
     */
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
        // 맵이 실제로 바뀐 경우에만 다시 심는다(BE-15) — 조회마다 세션 속성을 쓰면 세션 UPDATE가 매번
        // 한 번 더 생긴다. 바뀌었을 때는 갱신한 맵을 다시 심어야 Spring Session JDBC가 변경을 직렬화해
        // 저장한다(꺼내 온 참조를 제자리에서만 바꾸면 "바뀌지 않은 속성"으로 보일 수 있다).
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
     * 쿠키 값 형식: {@code postId:epochSeconds}를 {@code |}로 이은 문자열.
     * 쉼표는 Tomcat의 RFC 6265 쿠키 검증에서 거절되므로 응답에 쓰지 않는다.
     * 기존 쉼표 형식도 읽고, 형식이나 시간 범위가 이상하면 그 항목만 버린다.
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
        // 이 앱은 로컬 개발이 평문 HTTP라(application.yml) Secure를 무조건 걸면 로컬에서
        // 쿠키가 아예 저장되지 않는다. 운영은 항상 HTTPS 프록시 뒤에 있다(BE-01, request.isSecure()가
        // native forward-headers 전략 덕분에 프록시 뒤에서도 실제 프로토콜을 반영한다).
        cookie.setSecure(request.isSecure());
        response.addCookie(cookie);
    }
}
