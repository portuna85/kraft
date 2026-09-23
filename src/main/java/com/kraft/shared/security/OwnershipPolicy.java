package com.kraft.shared.security;

import com.kraft.config.security.KraftUserDetails;
import com.kraft.user.domain.Role;
import com.kraft.user.domain.User;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;

/**
 * "작성자 본인 또는 관리자만 수정·삭제할 수 있다"는 정책을 Post/Comment 등 여러 도메인에서
 * 공유하기 위한 순수 정적 유틸리티. 상태를 갖지 않으며, 실패 시 {@link AccessDeniedException}을
 * 던져 {@code ApiExceptionHandler}가 403으로 변환하도록 한다.
 * <p>
 * 같은 정책을 화면에서도 재사용한다. {@link #canManage(Authentication, User)}는 "관리 버튼을
 * 보여줄지"를 판정하는 boolean 버전이고, {@link #validateOwner}는 그 결과로 예외를 던지는
 * API 버전이다. 두 경로가 같은 한 벌의 규칙을 쓰도록 validateOwner가 canManage에 위임한다.
 */
public final class OwnershipPolicy {

    private OwnershipPolicy() {
    }

    /**
     * 화면에서 관리 행동(수정·삭제)을 노출해도 되는지 판정한다. 비로그인(익명 토큰 포함)이거나
     * 소유자를 알 수 없으면 false다. 예외를 던지지 않으므로 조회 경로에서 안전하게 쓸 수 있다.
     */
    public static boolean canManage(Authentication authentication, User owner) {
        if (!isAuthenticated(authentication)) {
            return false;
        }

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(Role.ADMIN.getKey()));
        boolean isOwner = owner != null && isSamePrincipal(authentication, owner);

        return isAdmin || isOwner;
    }

    /**
     * 이메일이 아니라 불변 userId로 소유자를 판정한다(개선 보고서 COR-02). 탈퇴한 이메일은
     * 재사용될 수 있다 — 세션 폐기가 지연된 옛 세션이 남아 있는 채로 같은 이메일로 새 계정이
     * 가입하면, 이메일만 비교하는 판정은 그 옛 세션을 새 계정의 소유자로 착각한다.
     * <p>
     * principal이 {@link KraftUserDetails}가 아니면(단위 테스트가 이메일 문자열만으로
     * {@code Authentication}을 만드는 경우가 많다) 예전처럼 이메일로 비교한다 — 실제 운영
     * 로그인은 {@code UserDetailsServiceImpl}이 항상 KraftUserDetails를 principal로 만들므로
     * 이 폴백을 타지 않는다.
     */
    private static boolean isSamePrincipal(Authentication authentication, User owner) {
        if (authentication.getPrincipal() instanceof KraftUserDetails principal) {
            return owner.getId() != null && owner.getId().equals(principal.getUserId());
        }
        return owner.getEmail().equals(authentication.getName());
    }

    /**
     * Thymeleaf의 {@code sec:authorize="isAuthenticated()"}와 같은 판정. 화면을 서버가
     * 렌더링하지 않는 곳(Vue 아일랜드에 내려줄 초기 상태 등)에서도 같은 규칙을 쓰기 위해 뺐다.
     */
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
