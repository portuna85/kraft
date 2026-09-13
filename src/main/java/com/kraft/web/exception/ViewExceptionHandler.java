package com.kraft.web.exception;

import com.kraft.web.IndexController;
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
@ControllerAdvice(assignableTypes = IndexController.class)
public class ViewExceptionHandler {

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(PostNotFoundException.class)
    public String handlePostNotFound(Model model) {
        model.addAttribute("pageTitle", "페이지를 찾을 수 없음");
        return "error/not-found";
    }
}
