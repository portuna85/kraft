package com.kraft.post.service;

import com.kraft.post.domain.Category;
import com.kraft.shared.security.OwnershipPolicy;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

import java.util.Arrays;
import java.util.List;

/** "공지(NOTICE) 분류는 관리자만 쓸 수 있다"는 제품 정책. 화면이 NOTICE 옵션을 숨기는 것은 안내일 뿐이고, 실제 경계는 생성·수정 양쪽에서 이 정책이 잡는다. */
public final class CategoryPolicy {

    private CategoryPolicy() {
    }

    public static void requireCanUse(Authentication authentication, Category category) {
        if (category != Category.NOTICE) {
            return;
        }

        if (!OwnershipPolicy.isAdmin(authentication)) {
            throw new AccessDeniedException("공지 분류는 관리자만 사용할 수 있습니다.");
        }
    }

    /**
     * 게시글 편집 화면(Vue 아일랜드)이 고를 수 있는 분류 목록. 이미 공지인 글은 관리자가
     * 아닌 사람이 편집해도 현재 값은 계속 보여야 하므로 {@code current}는 항상 포함한다.
     * post-update.html의 옛 th:each/th:if 필터를 그대로 옮긴 것이다.
     */
    public static List<Category> availableCategoriesFor(Authentication authentication, Category current) {
        boolean isAdmin = OwnershipPolicy.isAdmin(authentication);
        return Arrays.stream(Category.values())
                .filter(c -> c != Category.NOTICE || c == current || isAdmin)
                .toList();
    }
}
