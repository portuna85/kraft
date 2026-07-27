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

    private final ETagVersionProvider eTagVersionProvider;

    public PublicApiCacheControlFilter(ETagVersionProvider eTagVersionProvider) {
        this.eTagVersionProvider = eTagVersionProvider;
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
                if (etag != null && etag.equals(request.getHeader(HttpHeaders.IF_NONE_MATCH))) {
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
        String semantic = eTagVersionProvider.etagForPath(path);
        if (semantic != null) {
            return semantic;
        }
        // 도메인 버전을 알 수 없는 경로(예: /api/v1/status/incidents)만 MD5 폴백
        byte[] body = responseWrapper.getContentAsByteArray();
        return body.length > 0 ? "\"" + DigestUtils.md5DigestAsHex(body) + "\"" : null;
    }

    private static boolean isCacheablePath(String path) {
        return path.startsWith("/api/v1/stats/")
                || path.equals("/api/v1/rounds/latest")
                || path.equals("/api/v1/rounds/freshness")
                || path.equals("/api/v1/status/incidents")
                || path.equals("/api/v1/home");
    }
}
