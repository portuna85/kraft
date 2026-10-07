package com.kraft.post.web;

import com.kraft.post.domain.PostHiddenException;
import com.kraft.post.domain.PostNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 화면 컨트롤러 전용 예외 처리기. 브라우저가 렌더링하는 요청에는 JSON({@code ProblemDetail})이
 * 아니라 HTML 안내 화면을 돌려줘야 하므로 {@code ApiExceptionHandler}와 분리한다.
 * <p>
 * {@link PostNotFoundException}만 404로 변환한다. 모든 {@link IllegalArgumentException}을
 * 404로 치환하면 "없는 게시글"과 그 밖의 잘못된 요청을 구별할 수 없기 때문이다.
 */
@ControllerAdvice(assignableTypes = PostPageController.class)
public class ViewExceptionHandler {

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(PostNotFoundException.class)
    public String handlePostNotFound(Model model) {
        model.addAttribute("pageTitle", "페이지를 찾을 수 없음");
        return "error/not-found";
    }

    /**
     * 관리자가 숨긴 글. 상태 코드는 없는 글과 같지만 안내 문구가 다르다 — 사용자가 "내가 지웠나?"로
     * 헷갈리지 않게 한다. 더 가까운 타입이라 {@link #handlePostNotFound}보다 먼저 선택된다.
     */
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(PostHiddenException.class)
    public String handlePostHidden(Model model) {
        model.addAttribute("pageTitle", "숨겨진 게시글");
        model.addAttribute("hiddenByAdmin", true);
        return "error/not-found";
    }
}
