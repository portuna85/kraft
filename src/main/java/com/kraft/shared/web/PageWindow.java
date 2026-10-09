package com.kraft.shared.web;

import java.util.List;
import java.util.stream.IntStream;

/**
 * 목록 화면의 페이지 이동 표시에 필요한 값을 미리 계산한 화면 모델.
 * <p>
 * 전체 페이지 번호를 모두 출력하면 페이지가 많을 때 화면 폭을 넘기므로, 현재 페이지 주변의
 * 연속된 번호 최대 {@value #WINDOW_SIZE}개만 노출한다. Thymeleaf 표현식에서 계산하지 않고
 * 여기서 끝내는 이유는 경계 보정(첫·마지막·범위 초과 페이지) 규칙을 단위 테스트로 고정하기
 * 위해서다.
 * <p>
 * 모든 페이지 번호는 요청과 동일한 <b>0-기반</b>이다. 화면에 1부터 표시하는 변환은 템플릿이 한다.
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
     * 전체 페이지 수를 모를 때(검색) 쓰는 창. 번호 목록 없이 이전·다음만 있고
     * {@code totalPages}는 0이다. 화면은 {@code displayPage}("N페이지")만 보여 준다.
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
