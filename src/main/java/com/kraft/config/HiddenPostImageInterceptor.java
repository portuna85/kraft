package com.kraft.config;

import com.kraft.post.service.PostImageVisibility;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/** {@code /images/**} 요청 중 숨김·삭제된 글의 이미지는 파일을 읽기 전에 404로 막는다. 경로의 마지막 구간이 업로드 파일명(UUID.확장자)이다. 빈이 아니라 {@link WebConfig}가 만든다(@WebMvcTest 슬라이스는 HandlerInterceptor 빈을 스캔하는데 거기에는 서비스 빈이 없다). */
public class HiddenPostImageInterceptor implements HandlerInterceptor {

    private static final String PREFIX = "/images/";
    /** 파일명으로 쓸 수 없는 길이는 대장에 있을 수 없다 — 조회 없이 통과시켜 정적 처리가 404를 내게 한다. */
    private static final int MAX_FILE_NAME_LENGTH = 200;

    private final PostImageVisibility visibility;

    public HiddenPostImageInterceptor(PostImageVisibility visibility) {
        this.visibility = visibility;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!path.startsWith(PREFIX)) {
            return true;
        }
        String fileName = path.substring(PREFIX.length());
        if (fileName.isEmpty() || fileName.length() > MAX_FILE_NAME_LENGTH || fileName.indexOf('/') >= 0) {
            return true;
        }
        if (visibility.isBlocked(fileName)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return false;
        }
        return true;
    }
}
