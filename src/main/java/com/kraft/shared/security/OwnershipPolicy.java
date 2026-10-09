package com.kraft.shared.security;

import com.kraft.config.security.KraftUserDetails;
import com.kraft.user.domain.Role;
import com.kraft.user.domain.User;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;

/**
 * "작성자 본인 또는 관리자만 수정·삭제할 수 있다"는 정책을 도메인 간에 공유하는 순수 정적 유틸. {@link #canManage}는 화면의
 * 관리 버튼 노출용 boolean 판정이고, {@link #validateOwner}는 같은 규칙으로 {@link AccessDeniedException}(→ 403)을 던진다.
 */
public final class OwnershipPolicy {

    private OwnershipPolicy() {
    }

    /**
    /** 관리 행동을 노출해도 되는지. 비로그인(익명 토큰 포함)이거나 소유자를 모르면 false이며 예외를 던지지 않는다. */
    public static boolean canManage(Authentication authentication, User owner) {
        if (!isAuthenticated(authentication)) {
            return false;
        }

        boolean isOwner = owner != null && isSamePrincipal(authentication, owner);

        return isAdmin(authentication) || isOwner;
    }

    /** 관리자 권한 보유 여부(비로그인이면 false). 관리자 판정은 이 한 곳에서만 한다. */
    public static boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(Role.ADMIN.getKey()));
    }

    /**
     * 이메일이 아니라 불변 userId로 소유자를 판정한다 — 탈퇴한 이메일은 재사용될 수 있어 이메일 비교는 지연된 옛 세션을
     * 새 계정의 소유자로 착각한다. 운영 principal은 항상 {@link KraftUserDetails}라, 그 외 타입은 소유자가 아니다.
     */
    private static boolean isSamePrincipal(Authentication authentication, User owner) {
        return authentication.getPrincipal() instanceof KraftUserDetails principal
                && owner.getId() != null && owner.getId().equals(principal.getUserId());
    }

    /** Thymeleaf {@code sec:authorize="isAuthenticated()"}와 같은 판정 — 서버가 렌더링하지 않는 곳(Vue 초기 상태 등)에서도 같은 규칙을 쓰려고 뺐다. */
    public static boolean isAuthenticated(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    public static void validateOwner(Authentication authentication, User owner, Long entityId) {
        if (!canManage(authentication, owner)) {
            throw new AccessDeniedException("작성자 본인 또는 관리자만 수정·삭제할 수 있습니다. id=" + entityId);
        }
    }
}
