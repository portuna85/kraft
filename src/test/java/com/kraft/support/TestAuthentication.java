package com.kraft.support;

import com.kraft.config.security.KraftUserDetails;
import com.kraft.user.domain.Role;
import com.kraft.user.domain.User;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

/**
 * 테스트에서 로그인한 사용자를 흉내 낼 {@link Authentication}을 만든다.
 * <p>
 * 운영 로그인은 {@code UserDetailsServiceImpl}이 항상 {@link KraftUserDetails}를 만들므로 테스트도 운영과 같은 모양의 principal을 이 헬퍼로 만든다.
 * 이메일 문자열을 principal로 넣으면 {@code CurrentUser}·{@code OwnershipPolicy}가 미인증으로 본다.
 */
public final class TestAuthentication {

    private TestAuthentication() {
    }

    public static Authentication of(User user) {
        return of(user.getId(), user.getName(), user.getRole());
    }

    public static Authentication of(Long userId, String displayName, Role role) {
        KraftUserDetails principal = new KraftUserDetails(userId, "encoded", displayName,
                List.of(new SimpleGrantedAuthority(role.getKey())));
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
