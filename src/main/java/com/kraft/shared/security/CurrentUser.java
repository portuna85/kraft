package com.kraft.shared.security;

import com.kraft.config.security.KraftUserDetails;
import com.kraft.shared.exception.NotFoundException;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import org.springframework.security.core.Authentication;

import java.util.Optional;

/**
 * 인증된 principal에서 현재 사용자를 얻는다(개선 보고서 COR-02). {@link KraftUserDetails}가
 * 담고 있는 불변 userId를 쓴다 — 이메일은 탈퇴 후 재사용될 수 있다. 세션 폐기가 지연된
 * 옛 세션이 남아 있는 채로 같은 이메일로 새 계정이 가입하면, 이메일로 다시 조회하는 방식은
 * 그 사이 가입한 새 계정을 옛 세션의 주인으로 착각한다 — userId는 로그인 시점에 세션에 고정된
 * 값이라 이 문제가 없다.
 * <p>
 * principal이 KraftUserDetails가 아니면 미인증으로 본다(A-QA-02) — 실제 운영 로그인은
 * {@code UserDetailsServiceImpl}이 항상 KraftUserDetails를 principal로 만들므로, 그 외의
 * principal 타입은 운영에서 나올 수 없는 형태다. 이메일 해시로 다시 조회하던 예전 폴백은
 * principal 이름이 회원 id로 바뀐 뒤로는 운영에서 절대 타지 않는 죽은 코드였다(테스트에는
 * {@code TestAuthentication}을 쓴다).
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    /** principal이 KraftUserDetails일 때만 값이 있다. */
    public static Optional<Long> userId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof KraftUserDetails principal) {
            return Optional.ofNullable(principal.getUserId());
        }
        return Optional.empty();
    }

    /**
     * 로그인 여부와 무관하게 현재 사용자의 id를 얻는다. 익명·미인증이면 null이다
     * (조회 화면에서 "내가 쓴 글인가"처럼 로그인 여부에 따라 갈리는 판정에 쓴다).
     */
    public static Long userIdOrNull(Authentication authentication, UserRepository userRepository) {
        if (!OwnershipPolicy.isAuthenticated(authentication)) {
            return null;
        }
        return userId(authentication).orElse(null);
    }

    /**
     * @throws NotFoundException 계정을 찾지 못하면(탈퇴 등, BE-07), 또는 principal이
     *                            KraftUserDetails가 아니면(운영에서는 일어나지 않는다).
     */
    public static User require(Authentication authentication, UserRepository userRepository) {
        Long id = userId(authentication)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 회원입니다."));
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 회원입니다. userId=" + id));
        // userId는 로그인 시점에 세션에 고정된 값이라, 그 뒤 이 계정이 탈퇴해도 세션 자체는
        // (폐기가 지연되는 한) 계속 인증된 상태로 남는다. UserDetailsServiceImpl은 로그인
        // 시점에만 탈퇴 여부를 본다 — 여기서 한 번 더 걸러야 폐기가 끝나기 전까지 탈퇴한
        // 계정으로 새 글·댓글을 쓸 수 있는 창이 남지 않는다.
        if (user.isWithdrawn()) {
            throw new NotFoundException("존재하지 않는 회원입니다. userId=" + id);
        }
        return user;
    }
}
