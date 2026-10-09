package com.kraft.shared.web;

import jakarta.servlet.http.HttpServletRequest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * 로그인·로그아웃 후 돌아갈 주소가 이 앱 안인지 판정하는 순수 정적 유틸. 문자열 검사만으로는 브라우저의 URL 해석과
 * 어긋난다 — {@code /\attacker.example}은 WHATWG 파서가 {@code //}처럼 외부 호스트로 읽고, Referer를
 * {@code startsWith(baseUrl)}로 비교하면 {@code http://localhost.attacker.example}을 같은 오리진으로 착각한다.
 */
public final class SafeRedirect {

    private SafeRedirect() {
    }

    /**
     * 앱 내부 경로로 확정되면 그 값을, 아니면 {@code fallback}을 돌려준다. 조건: {@code /}로 시작하고 {@code //}로
     * 시작하지 않으며, 역슬래시·제어문자가 없고, URI로 파싱했을 때 scheme과 authority가 없을 것.
     */
    public static String internalPath(String candidate, String fallback) {
        if (candidate == null || candidate.isBlank()) {
            return fallback;
        }
        if (!candidate.startsWith("/") || candidate.startsWith("//")) {
            return fallback;
        }
        // 역슬래시는 브라우저가 "/"로 취급해 "/\host"가 "//host"가 되고, 제어문자는 파서마다 해석이 갈리며 헤더 분리에도 쓰인다.
        if (hasBackslashOrControlChar(candidate)) {
            return fallback;
        }

        try {
            URI uri = new URI(candidate);
            if (uri.getScheme() != null || uri.getAuthority() != null) {
                return fallback;
            }
        } catch (URISyntaxException e) {
            return fallback;
        }

        return candidate;
    }

    /**
     * Referer가 이 요청과 같은 오리진(scheme·host·유효 포트)일 때만 그 경로(path + query)를, 아니면 {@code null}을
     * 돌려준다. 원본 문자열이 아니라 경로만 써서 오리진 판정과 리다이렉트 대상이 어긋나지 않게 한다.
     */
    public static String sameOriginPathOf(String referer, HttpServletRequest request) {
        if (referer == null || referer.isBlank() || request == null) {
            return null;
        }

        URI uri;
        try {
            uri = new URI(referer);
        } catch (URISyntaxException e) {
            return null;
        }
        if (!uri.isAbsolute() || uri.getHost() == null) {
            return null;
        }

        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals(request.getScheme().toLowerCase(Locale.ROOT))
                || !uri.getHost().equalsIgnoreCase(request.getServerName())
                || effectivePort(uri.getPort(), scheme) != request.getServerPort()) {
            return null;
        }

        String path = (uri.getRawPath() == null || uri.getRawPath().isEmpty()) ? "/" : uri.getRawPath();
        return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
    }

    /** 포트가 생략된 URL은 scheme의 기본 포트를 쓴 것으로 본다. */
    private static int effectivePort(int port, String scheme) {
        if (port != -1) {
            return port;
        }
        return "https".equals(scheme) ? 443 : 80;
    }

    private static boolean hasBackslashOrControlChar(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' || c < 0x20 || c == 0x7F) {
                return true;
            }
        }
        return false;
    }
}
