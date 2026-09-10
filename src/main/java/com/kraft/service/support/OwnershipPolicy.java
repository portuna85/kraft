package com.kraft.service.support;

import com.kraft.domain.user.Role;
import com.kraft.domain.user.User;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

/**
 * "작성자 본인 또는 관리자만 수정·삭제할 수 있다"는 정책을 Post/Comment 등 여러 도메인에서
 * 공유하기 위한 순수 정적 유틸리티. 상태를 갖지 않으며, 실패 시 {@link AccessDeniedException}을
 * 던져 {@code ApiExceptionHandler}가 403으로 변환하도록 한다.
 */
public final class OwnershipPolicy {

    private OwnershipPolicy() {
    }

    public static void validateOwner(Authentication authentication, User owner, Long entityId) {
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(Role.ADMIN.getKey()));
        boolean isOwner = owner != null && owner.getEmail().equals(authentication.getName());

        if (!isAdmin && !isOwner) {
            throw new AccessDeniedException("작성자 본인 또는 관리자만 수정·삭제할 수 있습니다. id=" + entityId);
        }
    }
}
