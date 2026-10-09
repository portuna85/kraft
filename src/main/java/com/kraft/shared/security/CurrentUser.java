package com.kraft.shared.security;

import com.kraft.config.security.KraftUserDetails;
import com.kraft.shared.exception.NotFoundException;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import org.springframework.security.core.Authentication;

import java.util.Optional;

/**
 * 인증된 principal에서 현재 사용자를 얻는다. {@link KraftUserDetails}의 불변 userId를 쓴다 — 이메일은 탈퇴 후 재사용될 수
 * 있어, 이메일로 다시 조회하면 지연 폐기된 옛 세션을 같은 이메일로 가입한 새 계정의 주인으로 착각한다. 운영 principal은 항상
 * KraftUserDetails라, 그 외 타입은 미인증으로 본다.
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

    /** 현재 사용자의 id. 익명·미인증이면 null이다(로그인 여부에 따라 갈리는 판정용). */
    public static Long userIdOrNull(Authentication authentication, UserRepository userRepository) {
        if (!OwnershipPolicy.isAuthenticated(authentication)) {
            return null;
        }
        return userId(authentication).orElse(null);
    }

    /** @throws NotFoundException 계정을 찾지 못하거나(탈퇴 등) principal이 KraftUserDetails가 아닐 때 */
    public static User require(Authentication authentication, UserRepository userRepository) {
        Long id = userId(authentication)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 회원입니다."));
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 회원입니다. userId=" + id));
        // userId는 로그인 때 고정된 값이라, 폐기가 끝나기 전까지 탈퇴한 계정으로 글을 쓰지 못하게 여기서도 탈퇴 여부를 거른다.
        if (user.isWithdrawn()) {
            throw new NotFoundException("존재하지 않는 회원입니다. userId=" + id);
        }
        return user;
    }
}
