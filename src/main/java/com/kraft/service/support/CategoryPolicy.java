package com.kraft.service.support;

import com.kraft.domain.post.Category;
import com.kraft.domain.user.Role;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

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

        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(Role.ADMIN.getKey()));
        if (!isAdmin) {
            throw new AccessDeniedException("공지 분류는 관리자만 사용할 수 있습니다.");
        }
    }
}
