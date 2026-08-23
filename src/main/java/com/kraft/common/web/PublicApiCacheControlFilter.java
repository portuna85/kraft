package com.kraft.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class PublicApiCacheControlFilter extends OncePerRequestFilter {

    private static final String SHORT_PUBLIC_CACHE = "public, max-age=60, must-revalidate";

    private final EtagVersionSource etagVersionSource;

    public PublicApiCacheControlFilter(EtagVersionSource etagVersionSource) {
        this.etagVersionSource = etagVersionSource;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.GET.matches(request.getMethod()) || !isCacheablePath(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        boolean notModified = false;
        try {
            filterChain.doFilter(request, responseWrapper);

            if (responseWrapper.getStatus() >= 200 && responseWrapper.getStatus() < 300) {
                responseWrapper.setHeader(HttpHeaders.CACHE_CONTROL, SHORT_PUBLIC_CACHE);
                String etag = responseWrapper.getHeader(HttpHeaders.ETAG);
                if (etag == null) {
                    etag = resolveETag(request.getRequestURI(), responseWrapper);
                    if (etag != null) {
                        responseWrapper.setHeader(HttpHeaders.ETAG, etag);
                    }
                }
                if (etag != null && matchesIfNoneMatch(etag, request.getHeader(HttpHeaders.IF_NONE_MATCH))) {
                    // 304는 본문이 없어야 하므로 캐시된 본문은 wrapper에 버려두고 응답에는 복사하지 않는다.
                    notModified = true;
                    response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                    response.setHeader(HttpHeaders.CACHE_CONTROL, SHORT_PUBLIC_CACHE);
                    response.setHeader(HttpHeaders.ETAG, etag);
                }
            }
        } finally {
            if (!notModified) {
                responseWrapper.copyBodyToResponse();
            }
        }
    }

    private String resolveETag(String path, ContentCachingResponseWrapper responseWrapper) {
        String semantic = etagVersionSource.etagForPath(path);
        if (semantic != null) {
            return semantic;
        }
        // 도메인 버전을 알 수 없는 경로(예: /api/v1/status/incidents)만 MD5 폴백
        byte[] body = responseWrapper.getContentAsByteArray();
        return body.length > 0 ? "\"" + DigestUtils.md5DigestAsHex(body) + "\"" : null;
    }

    /**
     * L-03: RFC 7232 §2.3.2의 약한 비교로 If-None-Match를 판정한다.
     * 정확한 문자열 일치만 보면 {@code W/"..."} 약한 태그, 쉼표로 나열된 다중 검증자,
     * {@code *} 와일드카드를 쓰는 표준 준수 클라이언트가 304 기회를 놓친다. GET 조건부
     * 요청에는 약한 비교로 충분하다(본문 바이트 단위 동일성까지 요구하지 않는다).
     */
    private static boolean matchesIfNoneMatch(String etag, String header) {
        if (header == null) {
            return false;
        }
        String trimmedHeader = header.trim();
        if (trimmedHeader.equals("*")) {
            return true;
        }
        String normalizedEtag = stripWeakPrefix(etag);
        for (String candidate : trimmedHeader.split(",")) {
            if (stripWeakPrefix(candidate.trim()).equals(normalizedEtag)) {
                return true;
            }
        }
        return false;
    }

    private static String stripWeakPrefix(String value) {
        return value.startsWith("W/") ? value.substring(2) : value;
    }

    private static boolean isCacheablePath(String path) {
        return path.startsWith("/api/v1/stats/")
                || path.equals("/api/v1/rounds/latest")
                || path.equals("/api/v1/rounds/freshness")
                || path.equals("/api/v1/status/incidents");
    }
}
