package com.kraft.shared.web;

import org.springframework.web.util.UriComponentsBuilder;

import java.util.Optional;

/**
 * 목록 화면이 범위를 넘는 {@code page}를 받았을 때 마지막 페이지로 보내는 리다이렉트.
 * 항목이 처리되며 줄어드는 목록(신고 대기·정지 회원)은 북마크해 둔 페이지 번호가 나중에 범위를
 * 넘을 수 있고, 그대로 두면 빈 목록과 "현재"가 없는 페이지 이동 링크만 보인다.
 * <p>
 * 검색어·분류처럼 유지할 쿼리가 더 있는 게시판 목록은 조건이 달라 직접 만든다
 * ({@code PostPageController}).
 */
public final class PageRedirect {

    private PageRedirect() {
    }

    /**
     * @param pageNumber 요청받은 0 기반 페이지 번호
     * @param totalPages 전체 페이지 수(항목이 없으면 0)
     * @return 범위를 넘었으면 마지막 페이지로 가는 {@code redirect:} 문자열, 아니면 비어 있다
     */
    public static Optional<String> pastLastPage(String path, int pageNumber, int totalPages) {
        if (totalPages <= 0 || pageNumber < totalPages) {
            return Optional.empty();
        }
        return Optional.of("redirect:" + UriComponentsBuilder.fromPath(path)
                .queryParam("page", totalPages - 1)
                .build()
                .toUriString());
    }
}
