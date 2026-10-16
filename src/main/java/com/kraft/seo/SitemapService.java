package com.kraft.seo;

import com.kraft.post.domain.PostRepository;
import com.kraft.shared.web.BaseUrl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 공개 URL만 담은 sitemap을 만든다. 숨김·삭제된 글은 싣지 않는다. 글이 많아질 때 매 요청마다 훑지 않도록 결과를 잠시 보관한다.
 * <p>
 * 공개 글이 {@link #MAX_POSTS} 이하이면 {@code /sitemap.xml} 한 파일(urlset)이다. 넘으면 {@code /sitemap.xml}은 sitemap index가 되고,
 * 게시글은 id 순서의 keyset 구간으로 나눈 하위 파일({@code /sitemap-posts-{afterId}.xml}: id가 afterId보다 큰 글 최대 {@code MAX_POSTS}개)에 담긴다.
 * 구간 경계가 id라서 글이 지워져도 다른 구간의 내용이 밀리지 않고, 모든 공개 글이 정확히 한 파일에 들어간다. 목록·추천 페이지는 첫 구간({@code afterId=0})에 싣는다.
 */
@Service
public class SitemapService {

    /** sitemap 한 파일의 프로토콜 상한(50,000 URL)에서 목록·추천 페이지 몫을 뺀 게시글 수. */
    static final int MAX_POSTS = 49_990;

    private static final Duration TTL = Duration.ofMinutes(10);

    private final PostRepository postRepository;
    private final String baseUrl;
    private final boolean recommendEnabled;
    private final int maxPosts;

    private volatile Cached cached;

    /**
     * 한 번 만든 결과. {@code chunks}는 분할됐을 때만 채워지는 afterId → 하위 sitemap XML이다(index에 적힌 경계만 담으므로 요청이 임의의 값으로
     * 저장소를 두드릴 수 없다).
     */
    private record Cached(String xml, Map<Long, String> chunks, long expiresAtNanos) {
    }

    @Autowired
    public SitemapService(PostRepository postRepository,
                          @Value("${app.base-url}") String baseUrl,
                          @Value("${app.recommend.enabled:true}") boolean recommendEnabled) {
        this(postRepository, baseUrl, recommendEnabled, MAX_POSTS);
    }

    SitemapService(PostRepository postRepository, String baseUrl, boolean recommendEnabled, int maxPosts) {
        this.postRepository = postRepository;
        this.baseUrl = BaseUrl.normalize(baseUrl);
        this.recommendEnabled = recommendEnabled;
        this.maxPosts = maxPosts;
    }

    /** {@code /sitemap.xml}: 한 파일이면 urlset, 분할되면 sitemap index. */
    public String sitemapXml() {
        return current().xml();
    }

    /** {@code /sitemap-posts-{afterId}.xml}: index가 가리키는 구간만 돌려준다. 분할되지 않았거나 모르는 경계면 비어 있다. */
    public Optional<String> postsChunkXml(long afterId) {
        return Optional.ofNullable(current().chunks().get(afterId));
    }

    private Cached current() {
        Cached current = cached;
        long now = System.nanoTime();
        if (current != null && now - current.expiresAtNanos() < 0) {
            return current;
        }
        Cached built = build(now + TTL.toNanos());
        cached = built;
        return built;
    }

    private Cached build(long expiresAtNanos) {
        Map<Long, String> chunks = new LinkedHashMap<>();
        long after = 0;
        while (true) {
            // 한 개 더 읽어 다음 구간이 있는지 본다.
            List<PostRepository.SitemapRow> rows =
                    postRepository.findSitemapRowsAfter(after, PageRequest.of(0, maxPosts + 1));
            boolean hasMore = rows.size() > maxPosts;
            List<PostRepository.SitemapRow> chunkRows = hasMore ? rows.subList(0, maxPosts) : rows;
            chunks.put(after, urlset(after == 0, chunkRows));
            if (!hasMore) {
                break;
            }
            after = chunkRows.get(chunkRows.size() - 1).getId();
        }
        if (chunks.size() == 1) {
            return new Cached(chunks.get(0L), Map.of(), expiresAtNanos);
        }
        return new Cached(index(chunks.keySet()), Map.copyOf(chunks), expiresAtNanos);
    }

    private String urlset(boolean includePages, List<PostRepository.SitemapRow> rows) {
        StringBuilder xml = new StringBuilder(4096);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        if (includePages) {
            appendUrl(xml, "/community", null);
            if (recommendEnabled) {
                appendUrl(xml, "/recommend", null);
            }
        }
        for (PostRepository.SitemapRow row : rows) {
            appendUrl(xml, "/posts/update/" + row.getId(),
                    row.getUpdatedAt() == null ? null : row.getUpdatedAt().toLocalDate());
        }
        return xml.append("</urlset>\n").toString();
    }

    private String index(Iterable<Long> boundaries) {
        StringBuilder xml = new StringBuilder(512);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<sitemapindex xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        for (Long after : boundaries) {
            xml.append("  <sitemap><loc>").append(escape(baseUrl + "/sitemap-posts-" + after + ".xml"))
                    .append("</loc></sitemap>\n");
        }
        return xml.append("</sitemapindex>\n").toString();
    }

    private void appendUrl(StringBuilder xml, String path, LocalDate lastmod) {
        xml.append("  <url><loc>").append(escape(baseUrl + path)).append("</loc>");
        if (lastmod != null) {
            xml.append("<lastmod>").append(lastmod).append("</lastmod>");
        }
        xml.append("</url>\n");
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
