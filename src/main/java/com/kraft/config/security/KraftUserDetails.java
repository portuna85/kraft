package com.kraft.config.security;

import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

/**
 * 화면에 보여줄 이름(가입 시 입력한 닉네임)을 함께 들고 다니는 {@link org.springframework.security.core.userdetails.UserDetails}.
 * <p>
 * 로그인 아이디({@code getUsername()}, 곧 {@code Authentication.getName()})는 <b>불변 회원
 * 번호(id)의 문자열</b>이다(BE-04) — 예전에는 이메일을 그대로 썼는데, Spring Session JDBC가
 * {@code SPRING_SESSION.PRINCIPAL_NAME}과 직렬화된 SecurityContext에 이 값을 평문으로 저장해
 * {@code User.email}의 컬럼 암호화(EmailAttributeConverter)를 우회하는 경로가 됐다. 이메일이
 * 필요한 호출부는 {@link #getEmail()}을 쓴다. 화면에는 이메일 대신 {@code displayName}을
 * 보여준다({@code sec:authentication="principal.displayName"}).
 */
public class KraftUserDetails extends org.springframework.security.core.userdetails.User {

    private final Long userId;
    private final String email;
    private final String displayName;

    /**
     * @param userId 불변 회원 번호(B02). 로그인 성공 시 세션에도 심어 {@code SessionRevoker}가
     *               대상 계정을 구분한다({@code config.security.SecurityConfig#redirectAwareSuccessHandler} 참고).
     */
    public KraftUserDetails(Long userId, String email, String password, String displayName,
                            Collection<? extends GrantedAuthority> authorities) {
        super(String.valueOf(userId), password, authorities);
        this.userId = userId;
        this.email = email;
        this.displayName = displayName;
    }

    public Long getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }
}
