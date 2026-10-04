package com.kraft.shared.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PageRedirectTest {

    @Test
    @DisplayName("범위를 넘은 page는 마지막 페이지로 보낸다")
    void pastLastPage_redirectsToLastPage() {
        assertThat(PageRedirect.pastLastPage("/admin/reports", 5, 3)).contains("redirect:/admin/reports?page=2");
    }

    @Test
    @DisplayName("범위 안이면 리다이렉트하지 않는다")
    void withinRange_doesNotRedirect() {
        assertThat(PageRedirect.pastLastPage("/admin/reports", 2, 3)).isEmpty();
    }

    @Test
    @DisplayName("항목이 하나도 없으면(전체 0페이지) 리다이렉트하지 않는다")
    void emptyList_doesNotRedirect() {
        assertThat(PageRedirect.pastLastPage("/admin/reports", 4, 0)).isEmpty();
    }
}
