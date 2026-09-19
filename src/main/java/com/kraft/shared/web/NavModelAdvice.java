package com.kraft.shared.web;

import com.kraft.post.web.PostPageController;
import com.kraft.recommend.web.RecommendationPageController;
import com.kraft.report.web.AdminReportPageController;
import com.kraft.user.web.AdminUserPageController;
import com.kraft.user.web.UserPageController;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 공통 탐색이 현재 위치를 {@code aria-current="page"}로 표시할 수 있도록 요청 경로를 모델에
 * 넣어준다. Thymeleaf 3.1부터 템플릿에서 요청 객체({@code #request})에 직접 접근할 수 없으므로
 * 서버가 명시적으로 전달한다. 화면 컨트롤러에만 적용한다.
 * <p>
 * 관리자 화면(Admin*PageController)도 포함한다(F09) — 빠져 있으면 "신고 처리" 내비게이션이
 * 그 화면에 있을 때도 현재 위치로 표시되지 않고, 같은 모델 속성을 쓰는 로그인 링크의
 * {@code redirect} 복귀 주소도 비게 된다.
 */
@ControllerAdvice(assignableTypes = {
        PostPageController.class, UserPageController.class,
        AdminReportPageController.class, AdminUserPageController.class,
        RecommendationPageController.class})
public class NavModelAdvice {

    /**
     * 쿼리 문자열까지 포함한다. 이 값은 로그인 링크의 복귀 주소로도 쓰이는데, 경로만 담으면
     * 검색어나 페이지 번호를 보던 사용자가 로그인 후 목록 첫 화면으로 떨어진다.
     */
    @ModelAttribute("currentPath")
    public String currentPath(HttpServletRequest request) {
        String query = request.getQueryString();
        return query == null || query.isBlank()
                ? request.getRequestURI()
                : request.getRequestURI() + "?" + query;
    }
}
