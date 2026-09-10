package com.kraft.web.support;

import com.kraft.web.IndexController;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 공통 탐색이 현재 위치를 {@code aria-current="page"}로 표시할 수 있도록 요청 경로를 모델에
 * 넣어준다. Thymeleaf 3.1부터 템플릿에서 요청 객체({@code #request})에 직접 접근할 수 없으므로
 * 서버가 명시적으로 전달한다. 화면 컨트롤러에만 적용한다.
 */
@ControllerAdvice(assignableTypes = IndexController.class)
public class NavModelAdvice {

    @ModelAttribute("currentPath")
    public String currentPath(HttpServletRequest request) {
        return request.getRequestURI();
    }
}
