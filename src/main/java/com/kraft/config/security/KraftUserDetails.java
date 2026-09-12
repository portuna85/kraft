package com.kraft.config.security;

import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

/**
 * 화면에 보여줄 이름(가입 시 입력한 닉네임)을 함께 들고 다니는 {@link org.springframework.security.core.userdetails.UserDetails}.
 * <p>
 * 로그인 아이디({@code getUsername()})는 지금까지처럼 <b>이메일</b>이다 — 서비스 계층이
 * {@code authentication.getName()}으로 회원을 조회하므로 이 값은 절대 바꾸면 안 된다.
 * 헤더 같은 화면에는 이메일 대신 {@code displayName}을 보여준다
 * ({@code sec:authentication="principal.displayName"}).
 */
public class KraftUserDetails extends org.springframework.security.core.userdetails.User {

    private final String displayName;

    public KraftUserDetails(String email, String password, String displayName,
                            Collection<? extends GrantedAuthority> authorities) {
        super(email, password, authorities);
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
