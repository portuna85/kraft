package com.kraft.seo;

import com.kraft.post.domain.PostRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SitemapServiceTest {

    private final PostRepository postRepository = mock(PostRepository.class);

    private PostRepository.SitemapRow row(long id, LocalDateTime updatedAt) {
        PostRepository.SitemapRow row = mock(PostRepository.SitemapRow.class);
        when(row.getId()).thenReturn(id);
        when(row.getUpdatedAt()).thenReturn(updatedAt);
        return row;
    }

    @Test
    @DisplayName("게시글은 MAX_POSTS개까지만 요청한다")
    void requestsAtMostMaxPosts() {
        when(postRepository.findSitemapRows(any(Pageable.class))).thenReturn(List.of());

        new SitemapService(postRepository, "https://kraft.example", true).sitemapXml();

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(postRepository).findSitemapRows(page.capture());
        assertThat(page.getValue().getPageNumber()).isZero();
        assertThat(page.getValue().getPageSize()).isEqualTo(SitemapService.MAX_POSTS);
    }

    @Test
    @DisplayName("목록·추천 페이지와 게시글 URL을 담고, 수정일이 없으면 lastmod를 생략한다")
    void listsPagesAndPosts() {
        PostRepository.SitemapRow dated = row(7L, LocalDateTime.of(2026, 10, 1, 12, 0));
        PostRepository.SitemapRow undated = row(8L, null);
        when(postRepository.findSitemapRows(any(Pageable.class))).thenReturn(List.of(dated, undated));

        String xml = new SitemapService(postRepository, "https://kraft.example/", true).sitemapXml();

        assertThat(xml)
                .contains("<loc>https://kraft.example/community</loc>")
                .contains("<loc>https://kraft.example/recommend</loc>")
                .contains("<loc>https://kraft.example/posts/update/7</loc><lastmod>2026-10-01</lastmod>")
                .contains("<loc>https://kraft.example/posts/update/8</loc></url>");
    }

    @Test
    @DisplayName("추천 기능을 끄면 추천 페이지를 싣지 않는다")
    void omitsRecommendWhenDisabled() {
        when(postRepository.findSitemapRows(any(Pageable.class))).thenReturn(List.of());

        String xml = new SitemapService(postRepository, "https://kraft.example", false).sitemapXml();

        assertThat(xml).contains("/community").doesNotContain("/recommend");
    }

    @Test
    @DisplayName("TTL 안의 두 번째 호출은 저장소를 다시 읽지 않고 같은 결과를 돌려준다")
    void reusesCachedXmlWithinTtl() {
        when(postRepository.findSitemapRows(any(Pageable.class))).thenReturn(List.of());
        SitemapService service = new SitemapService(postRepository, "https://kraft.example", true);

        String first = service.sitemapXml();
        String second = service.sitemapXml();

        assertThat(second).isSameAs(first);
        verify(postRepository, times(1)).findSitemapRows(any(Pageable.class));
    }
}
