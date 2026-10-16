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
import static org.mockito.ArgumentMatchers.eq;
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
    @DisplayName("한 구간에는 MAX_POSTS개까지만 싣고, 다음 구간 유무를 보려고 하나만 더 읽는다")
    void requestsAtMostMaxPosts() {
        when(postRepository.findSitemapRowsAfter(eq(0L), any(Pageable.class))).thenReturn(List.of());

        new SitemapService(postRepository, "https://kraft.example", true).sitemapXml();

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(postRepository).findSitemapRowsAfter(eq(0L), page.capture());
        assertThat(page.getValue().getPageNumber()).isZero();
        assertThat(page.getValue().getPageSize()).isEqualTo(SitemapService.MAX_POSTS + 1); // 다음 구간이 있는지 보려고 하나 더 읽는다
    }

    @Test
    @DisplayName("목록·추천 페이지와 게시글 URL을 담고, 수정일이 없으면 lastmod를 생략한다")
    void listsPagesAndPosts() {
        PostRepository.SitemapRow dated = row(7L, LocalDateTime.of(2026, 10, 1, 12, 0));
        PostRepository.SitemapRow undated = row(8L, null);
        when(postRepository.findSitemapRowsAfter(eq(0L), any(Pageable.class))).thenReturn(List.of(dated, undated));

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
        when(postRepository.findSitemapRowsAfter(eq(0L), any(Pageable.class))).thenReturn(List.of());

        String xml = new SitemapService(postRepository, "https://kraft.example", false).sitemapXml();

        assertThat(xml).contains("/community").doesNotContain("/recommend");
    }

    @Test
    @DisplayName("TTL 안의 두 번째 호출은 저장소를 다시 읽지 않고 같은 결과를 돌려준다")
    void reusesCachedXmlWithinTtl() {
        when(postRepository.findSitemapRowsAfter(eq(0L), any(Pageable.class))).thenReturn(List.of());
        SitemapService service = new SitemapService(postRepository, "https://kraft.example", true);

        String first = service.sitemapXml();
        String second = service.sitemapXml();

        assertThat(second).isSameAs(first);
        verify(postRepository, times(1)).findSitemapRowsAfter(eq(0L), any(Pageable.class));
    }

    private void givenPosts(long afterId, int limit, long... ids) {
        List<PostRepository.SitemapRow> rows = new java.util.ArrayList<>();
        for (long id : ids) {
            rows.add(row(id, LocalDateTime.of(2026, 10, 1, 0, 0)));
        }
        when(postRepository.findSitemapRowsAfter(eq(afterId), org.mockito.ArgumentMatchers.argThat(
                (Pageable pageable) -> pageable != null && pageable.getPageSize() == limit))).thenReturn(rows);
    }

    @Test
    @DisplayName("공개 글이 상한을 넘으면 /sitemap.xml은 index가 되고 모든 글이 중복·누락 없이 하위 파일에 나뉘어 담긴다")
    void splitsIntoIndexAndChunksWithoutLossOrDuplicates() {
        // 상한 3: 글 id 1,2,3,5,6,8,9 (4·7은 삭제·숨김) → [1,2,3] [5,6,8] [9]
        givenPosts(0L, 4, 1, 2, 3, 5);
        givenPosts(3L, 4, 5, 6, 8, 9);
        givenPosts(8L, 4, 9);
        SitemapService service = new SitemapService(postRepository, "https://kraft.example", true, 3);

        String index = service.sitemapXml();

        assertThat(index).contains("<sitemapindex")
                .contains("<loc>https://kraft.example/sitemap-posts-0.xml</loc>")
                .contains("<loc>https://kraft.example/sitemap-posts-3.xml</loc>")
                .contains("<loc>https://kraft.example/sitemap-posts-8.xml</loc>")
                .doesNotContain("<urlset");

        String first = service.postsChunkXml(0).orElseThrow();
        String second = service.postsChunkXml(3).orElseThrow();
        String third = service.postsChunkXml(8).orElseThrow();
        assertThat(first).contains("/community</loc>").contains("/recommend</loc>")
                .contains("/posts/update/1<").contains("/posts/update/2<").contains("/posts/update/3<")
                .doesNotContain("/posts/update/5<");
        assertThat(second).doesNotContain("/community").contains("/posts/update/5<").contains("/posts/update/6<")
                .contains("/posts/update/8<").doesNotContain("/posts/update/9<");
        assertThat(third).contains("/posts/update/9<").doesNotContain("/posts/update/8<");

        String all = first + second + third;
        for (String id : new String[]{"1", "2", "3", "5", "6", "8", "9"}) {
            assertThat(all.split("/posts/update/" + id + "<", -1)).as("id %s", id).hasSize(2);
        }
    }

    @Test
    @DisplayName("글 수가 상한과 정확히 같으면 분할하지 않고 한 파일이다")
    void exactlyAtLimit_staysSingleFile() {
        givenPosts(0L, 4, 1, 2, 3);
        SitemapService service = new SitemapService(postRepository, "https://kraft.example", true, 3);

        String xml = service.sitemapXml();

        assertThat(xml).contains("<urlset").contains("/posts/update/3<").doesNotContain("sitemapindex");
        assertThat(service.postsChunkXml(0)).isEmpty();
    }

    @Test
    @DisplayName("index에 없는 경계의 하위 파일은 저장소를 읽지 않고 비어 있다")
    void unknownChunkBoundary_isEmpty() {
        givenPosts(0L, 4, 1, 2, 3, 5);
        givenPosts(3L, 4, 5);
        SitemapService service = new SitemapService(postRepository, "https://kraft.example", true, 3);

        assertThat(service.postsChunkXml(999)).isEmpty();
        verify(postRepository, times(2)).findSitemapRowsAfter(any(Long.class), any(Pageable.class));
    }

    @Test
    @DisplayName("분할된 결과도 TTL 안에서는 저장소를 다시 읽지 않는다")
    void splitResultIsCachedWithinTtl() {
        givenPosts(0L, 4, 1, 2, 3, 5);
        givenPosts(3L, 4, 5);
        SitemapService service = new SitemapService(postRepository, "https://kraft.example", true, 3);

        service.sitemapXml();
        service.postsChunkXml(0);
        service.postsChunkXml(3);
        service.sitemapXml();

        verify(postRepository, times(2)).findSitemapRowsAfter(any(Long.class), any(Pageable.class));
    }
}
