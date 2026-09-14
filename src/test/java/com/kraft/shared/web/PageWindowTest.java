package com.kraft.shared.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PageWindowTest {

    @Test
    @DisplayName("전체 페이지가 0이면 빈 창을 반환한다")
    void of_whenTotalPagesIsZero_returnsEmptyWindow() {
        PageWindow window = PageWindow.of(0, 0);

        assertThat(window.pages()).isEmpty();
        assertThat(window.hasPrev()).isFalse();
        assertThat(window.hasNext()).isFalse();
    }

    @Test
    @DisplayName("전체 페이지가 1이면 이전·다음이 모두 없다")
    void of_whenTotalPagesIsOne_hasNoPrevAndNoNext() {
        PageWindow window = PageWindow.of(0, 1);

        assertThat(window.pages()).containsExactly(0);
        assertThat(window.hasPrev()).isFalse();
        assertThat(window.hasNext()).isFalse();
        assertThat(window.displayPage()).isEqualTo(1);
        assertThat(window.totalPages()).isEqualTo(1);
    }

    @Test
    @DisplayName("첫 페이지에서는 이전이 없고 다음은 있다")
    void of_whenFirstPage_hasNoPrevAndHasNext() {
        PageWindow window = PageWindow.of(0, 7);

        assertThat(window.hasPrev()).isFalse();
        assertThat(window.hasNext()).isTrue();
        assertThat(window.next()).isEqualTo(1);
        assertThat(window.pages()).containsExactly(0, 1, 2, 3, 4);
    }

    @Test
    @DisplayName("마지막 페이지에서는 다음이 없고 이전은 있다")
    void of_whenLastPage_hasPrevAndNoNext() {
        PageWindow window = PageWindow.of(6, 7);

        assertThat(window.hasPrev()).isTrue();
        assertThat(window.hasNext()).isFalse();
        assertThat(window.prev()).isEqualTo(5);
        assertThat(window.pages()).containsExactly(2, 3, 4, 5, 6);
    }

    @Test
    @DisplayName("가운데 페이지는 앞뒤로 창이 균형 있게 잡힌다")
    void of_whenMiddlePage_balancesWindow() {
        PageWindow window = PageWindow.of(3, 7);

        assertThat(window.pages()).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    @DisplayName("범위를 초과한 페이지 요청은 마지막 페이지로 보정한다")
    void of_whenPageExceedsTotalPages_adjustsToLastPage() {
        PageWindow window = PageWindow.of(999, 3);

        assertThat(window.displayPage()).isEqualTo(3);
        assertThat(window.hasNext()).isFalse();
        assertThat(window.hasPrev()).isTrue();
        assertThat(window.pages()).containsExactly(0, 1, 2);
    }

    @Test
    @DisplayName("음수 페이지 요청은 첫 페이지로 보정한다")
    void of_whenPageIsNegative_adjustsToFirstPage() {
        PageWindow window = PageWindow.of(-1, 3);

        assertThat(window.displayPage()).isEqualTo(1);
        assertThat(window.hasPrev()).isFalse();
        assertThat(window.hasNext()).isTrue();
    }

    @Test
    @DisplayName("전체 페이지가 5개 이하이면 창이 전체 범위를 그대로 담는다")
    void of_whenTotalPagesIsFiveOrLess_containsAllPages() {
        PageWindow window = PageWindow.of(1, 3);

        assertThat(window.pages()).containsExactly(0, 1, 2);
    }
}
