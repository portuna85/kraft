package com.kraft.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Set;

/**
 * {@code /}는 화면이 아니라 입구다 — 번호 추천(꺼져 있으면 커뮤니티)으로 보낸다. 게시판 파라미터가 붙은 요청(옛 검색·정렬·페이지
 * 링크)은 {@code /community}로 영구 이동시켜 북마크와 검색 색인이 끊기지 않게 하고, 그 밖의 {@code /}는 임시 이동이다(첫 화면은
 * 기능 플래그에 따라 바뀔 수 있어 브라우저가 기억하면 안 된다).
 */
@RestController
public class RootRedirectController {

    private static final Set<String> LEGACY_BOARD_PARAMS = Set.of("q", "category", "scope", "sort", "page");

    private final boolean recommendEnabled;

    public RootRedirectController(@Value("${app.recommend.enabled:true}") boolean recommendEnabled) {
        this.recommendEnabled = recommendEnabled;
    }

    @GetMapping("/")
    public ResponseEntity<Void> root(HttpServletRequest request) {
        if (hasLegacyBoardParam(request)) {
            return redirect(HttpStatus.MOVED_PERMANENTLY, "/community?" + request.getQueryString());
        }
        return redirect(HttpStatus.FOUND, recommendEnabled ? "/recommend" : "/community");
    }

    private static ResponseEntity<Void> redirect(HttpStatus status, String location) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.LOCATION, URI.create(location).toASCIIString())
                .build();
    }

    private static boolean hasLegacyBoardParam(HttpServletRequest request) {
        String query = request.getQueryString();
        return query != null && !query.isBlank()
                && request.getParameterMap().keySet().stream().anyMatch(LEGACY_BOARD_PARAMS::contains);
    }
}
