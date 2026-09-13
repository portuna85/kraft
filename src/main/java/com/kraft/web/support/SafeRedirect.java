package com.kraft.web.support;

import jakarta.servlet.http.HttpServletRequest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * 로그인·로그아웃 후 돌아갈 주소가 정말 이 앱 안인지 판정하는 순수 정적 유틸리티.
 * {@code OwnershipPolicy}·{@code WriteAccessPolicy}와 같은 스타일이다.
 * <p>
 * 예전에는 {@code startsWith("/") && !startsWith("//")} 문자열 검사만 했는데, 이 규칙은
 * 브라우저의 URL 해석 규칙과 다르다 — {@code /\attacker.example/path}는 이 검사를 통과하지만
 * WHATWG URL 파서는 {@code //}와 똑같이 취급해 외부 호스트로 읽는다. Referer도
 * {@code startsWith(baseUrl)}로 비교해서 {@code http://localhost.attacker.example/}가
 * {@code http://localhost}와 같은 오리진으로 인정됐다(개선 보고서 F03).
 */
public final class SafeRedirect {

    private SafeRedirect() {
    }

    /**
     * 앱 내부 경로로 확정할 수 있으면 그 값을, 아니면 {@code fallback}을 돌려준다.
     * <p>
     * 통과 조건은 네 가지다: {@code /}로 시작하고, {@code //}로 시작하지 않고, 역슬래시나
     * 제어문자를 포함하지 않고, URI로 파싱했을 때 scheme과 authority가 모두 없을 것.
     */
    public static String internalPath(String candidate, String fallback) {
        if (candidate == null || candidate.isBlank()) {
            return fallback;
        }
        if (!candidate.startsWith("/") || candidate.startsWith("//")) {
            return fallback;
        }
        // 역슬래시는 브라우저가 "/"와 같게 취급하므로 "/\host"가 "//host"가 된다. 제어문자는
        // 파서마다 무시하거나 잘라내 해석이 갈리고, 응답 헤더 분리에도 쓰인다. 둘 다 거부한다.
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
     * Referer가 이 요청과 같은 오리진일 때만 그 경로(path + query)를, 아니면 {@code null}을
     * 돌려준다. scheme·host·유효 포트를 각각 비교하며, 통과해도 원본 문자열이 아니라 경로만
     * 쓴다 — 오리진 판정과 실제 리다이렉트 대상이 어긋날 여지를 남기지 않기 위해서다.
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
