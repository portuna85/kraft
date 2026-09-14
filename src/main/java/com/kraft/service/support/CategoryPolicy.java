package com.kraft.service.support;

import com.kraft.domain.post.Category;
import com.kraft.domain.user.Role;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

import java.util.Arrays;
import java.util.List;

/**
 * "공지(NOTICE) 분류는 관리자만 쓸 수 있다"는 제품 정책. 예전에는 서버에 이 제한이 없어
 * 일반 사용자도 NOTICE로 글을 쓸 수 있었다(개선 보고서 '성능·API·사용성 추가 의견').
 * <p>
 * 화면에서도 관리자가 아니면 NOTICE 옵션을 렌더링하지 않지만, 그것은 안내일 뿐이고
 * 실제 경계는 생성·수정 양쪽에서 이 정책이 잡는다.
 */
public final class CategoryPolicy {

    private CategoryPolicy() {
    }

    public static void requireCanUse(Authentication authentication, Category category) {
        if (category != Category.NOTICE) {
            return;
        }

        if (!isAdmin(authentication)) {
            throw new AccessDeniedException("공지 분류는 관리자만 사용할 수 있습니다.");
        }
    }

    /**
     * 게시글 편집 화면(Vue 아일랜드)이 고를 수 있는 분류 목록. 이미 공지인 글은 관리자가
     * 아닌 사람이 편집해도 현재 값은 계속 보여야 하므로 {@code current}는 항상 포함한다.
     * post-update.html의 옛 th:each/th:if 필터를 그대로 옮긴 것이다.
     */
    public static List<Category> availableCategoriesFor(Authentication authentication, Category current) {
        boolean isAdmin = isAdmin(authentication);
        return Arrays.stream(Category.values())
                .filter(c -> c != Category.NOTICE || c == current || isAdmin)
                .toList();
    }

    public static boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(Role.ADMIN.getKey()));
    }
}
