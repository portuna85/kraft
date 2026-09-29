package com.kraft.seo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * 검색엔진용 {@code /sitemap.xml}과 {@code /robots.txt}(P1-1). robots.txt를 정적 파일이 아니라
 * 여기서 내려주는 이유는 {@code Sitemap:} 줄에 절대 주소가 필요하고, 그 주소를 Host 헤더가
 * 아닌 설정값({@code app.base-url})으로 정하기 위해서다({@code SeoModelAdvice}와 같은 원칙).
 */
@RestController
public class SitemapController {

    private final SitemapService sitemapService;
    private final String baseUrl;

    public SitemapController(SitemapService sitemapService, @Value("${app.base-url}") String baseUrl) {
        this.sitemapService = sitemapService;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemap() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePublic())
                .body(sitemapService.sitemapXml());
    }

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> robots() {
        String body = """
                User-agent: *
                Disallow: /admin
                Disallow: /api/

                Sitemap: %s/sitemap.xml
                """.formatted(baseUrl);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .body(body);
    }
}
