package com.kraft.shared.security;

import com.kraft.user.domain.Role;
import com.kraft.user.domain.User;
import org.springframework.security.access.AccessDeniedException;

/**
 * "이메일 인증을 마쳐야(Role.GUEST가 아니어야) 글을 쓸 수 있다"는 정책을 Post/Comment 등
 * 여러 도메인의 작성(save) 경로에서 공유하기 위한 순수 정적 유틸리티. {@link OwnershipPolicy}와
 * 같은 스타일로, 실패 시 {@link AccessDeniedException}을 던져 {@code ApiExceptionHandler}가
 * 403으로 변환하도록 한다.
 */
public final class WriteAccessPolicy {

    private WriteAccessPolicy() {
    }

    public static void requireVerified(User user) {
        if (user.getRole() == Role.GUEST) {
            throw new AccessDeniedException("이메일 인증을 완료해야 글을 작성할 수 있습니다. id=" + user.getId());
        }
    }
}
