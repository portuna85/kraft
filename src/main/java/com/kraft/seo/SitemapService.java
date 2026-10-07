package com.kraft.seo;

import com.kraft.post.domain.PostRepository;
import com.kraft.shared.web.BaseUrl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/**
 * 공개 URL만 담은 sitemap을 만든다. 게시글은 삭제되면 행이 사라지고 비공개 개념이 없어
 * 남아 있는 글은 전부 공개다. 글이 많아질 때 매 요청마다 훑지 않도록 결과를 잠시 보관한다.
 */
@Service
public class SitemapService {

    /** sitemap 한 파일의 프로토콜 상한(50,000 URL)에서 홈·추천 몫을 뺀 게시글 수. */
    static final int MAX_POSTS = 49_990;

    private static final Duration TTL = Duration.ofMinutes(10);

    private final PostRepository postRepository;
    private final String baseUrl;
    private final boolean recommendEnabled;

    private volatile Cached cached;

    private record Cached(String xml, long expiresAtNanos) {
    }

    public SitemapService(PostRepository postRepository,
                          @Value("${app.base-url}") String baseUrl,
                          @Value("${app.recommend.enabled:true}") boolean recommendEnabled) {
        this.postRepository = postRepository;
        this.baseUrl = BaseUrl.normalize(baseUrl);
        this.recommendEnabled = recommendEnabled;
    }

    public String sitemapXml() {
        Cached current = cached;
        long now = System.nanoTime();
        if (current != null && now - current.expiresAtNanos() < 0) {
            return current.xml();
        }
        String xml = build();
        cached = new Cached(xml, now + TTL.toNanos());
        return xml;
    }

    String build() {
        StringBuilder xml = new StringBuilder(4096);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        appendUrl(xml, "/community", null);
        if (recommendEnabled) {
            appendUrl(xml, "/recommend", null);
        }
        List<PostRepository.SitemapRow> rows = postRepository.findSitemapRows(PageRequest.of(0, MAX_POSTS));
        for (PostRepository.SitemapRow row : rows) {
            appendUrl(xml, "/posts/update/" + row.getId(),
                    row.getUpdatedAt() == null ? null : row.getUpdatedAt().toLocalDate());
        }
        return xml.append("</urlset>\n").toString();
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
