package com.kraft.post.web;

/**
 * 검색 범위 쿼리 파라미터({@code scope})를 해석한다(전체 리뷰 2026-09-26 A-BE-02 2단계).
 * <p>
 * 값이 {@code "all"}일 때만 본문까지 검색한다. 그 외(없음·오타·다른 값)는 전부 기본값인
 * 제목만 검색으로 본다 — 잘못된 정렬·분류 값을 오류로 거절하지 않고 조용히 기본값으로
 * 물러서는 이 저장소의 기존 관례(예: {@code PostSortPolicy})와 같다.
 */
final class SearchScope {

    private static final String CONTENT = "all";

    private SearchScope() {
    }

    static boolean isContent(String scope) {
        return CONTENT.equals(scope);
    }
}
