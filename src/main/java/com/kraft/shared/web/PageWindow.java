package com.kraft.shared.web;

import java.util.List;
import java.util.stream.IntStream;

/**
 * 목록의 페이지 이동 표시 값을 미리 계산한 모델. 현재 페이지 주변의 연속된 번호 최대 {@value #WINDOW_SIZE}개만 노출하며, 경계
 * 보정(첫·마지막·범위 초과) 규칙을 템플릿이 아니라 여기서 끝내 단위 테스트로 고정한다. 페이지 번호는 모두 0-기반이다(1부터
 * 표시하는 변환은 템플릿).
 */
public record PageWindow(
        List<Integer> pages,
        boolean hasPrev,
        boolean hasNext,
        int prev,
        int next,
        int displayPage,
        int totalPages
) {

    private static final int WINDOW_SIZE = 5;

    /**
     * 전체 페이지 수를 모를 때(검색)의 창: 번호 목록 없이 이전·다음만 있고 {@code totalPages}는 0이다.
     *
     * @param page    현재 페이지(0-기반)
     * @param hasNext 다음 페이지가 있는지(Slice의 {@code hasNext})
     */
    public static PageWindow simple(int page, boolean hasNext) {
        int current = Math.max(page, 0);
        return new PageWindow(List.of(), current > 0, hasNext, current - 1, current + 1, current + 1, 0);
    }

    public static PageWindow of(int page, int totalPages) {
        if (totalPages <= 0) {
            return new PageWindow(List.of(), false, false, 0, 0, 0, 0);
        }

        // 범위를 벗어난 페이지 요청(?page=999)도 이동 링크만은 유효한 범위를 가리키게 보정한다.
        int current = Math.min(Math.max(page, 0), totalPages - 1);

        int start = Math.max(current - WINDOW_SIZE / 2, 0);
        int end = Math.min(start + WINDOW_SIZE, totalPages);
        start = Math.max(end - WINDOW_SIZE, 0);

        return new PageWindow(
                IntStream.range(start, end).boxed().toList(),
                current > 0,
                current < totalPages - 1,
                current - 1,
                current + 1,
                current + 1,
                totalPages
        );
    }
}
