package com.kraft.support;

import com.kraft.config.security.KraftUserDetails;
import com.kraft.user.domain.Role;
import com.kraft.user.domain.User;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

/**
 * 테스트에서 로그인한 사용자를 흉내 낼 {@link Authentication}을 만든다(전체 리뷰 2026-09-26
 * A-QA-02).
 * <p>
 * 예전에는 테스트가 이메일 문자열을 그대로 principal로 넣은
 * {@code new UsernamePasswordAuthenticationToken(email, ...)}을 흔히 썼다. 운영 로그인은
 * {@code UserDetailsServiceImpl}이 항상 {@link KraftUserDetails}를 만들므로, 그 문자열
 * principal은 {@code OwnershipPolicy}·{@code CurrentUser}의 "principal이 KraftUserDetails가
 * 아니면 이메일로 대신 조회"하는 폴백 분기만 태웠다 — 그 분기는 운영에서는 절대 타지 않는
 * 죽은 코드였는데도 테스트가 계속 살려 두고 있었다. 이 폴백을 걷어내면서, 테스트도 운영과
 * 같은 모양(KraftUserDetails)의 principal을 쓰도록 이 헬퍼로 모았다.
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
