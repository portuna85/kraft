package com.kraft.post.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchScopeTest {

    @Test
    @DisplayName("scope=all일 때만 본문까지 검색한다")
    void onlyAllSearchesContent() {
        assertThat(SearchScope.isContent("all")).isTrue();
    }

    @Test
    @DisplayName("없음·오타·다른 값은 모두 제목만 검색(기본값)으로 본다")
    void everythingElseFallsBackToTitleOnly() {
        assertThat(SearchScope.isContent(null)).isFalse();
        assertThat(SearchScope.isContent("")).isFalse();
        assertThat(SearchScope.isContent("ALL")).isFalse();
        assertThat(SearchScope.isContent("content")).isFalse();
        assertThat(SearchScope.isContent("title")).isFalse();
    }
}
