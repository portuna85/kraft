package com.kraft.config.security;

import org.springframework.security.core.GrantedAuthority;

import java.io.Serial;
import java.util.Collection;

/**
 * 화면에 보여줄 닉네임을 함께 들고 다니는 {@link org.springframework.security.core.userdetails.UserDetails}.
 * <p>
 * 로그인 아이디({@code getUsername()})는 불변 회원 번호(id)의 문자열이다. 이메일을 쓰면 Spring Session JDBC가
 * {@code SPRING_SESSION.PRINCIPAL_NAME}과 직렬화된 SecurityContext에 평문으로 저장해 컬럼 암호화를 우회한다. 같은 이유로
 * 이메일을 필드로 들고 다니지 않는다(이 객체 전체가 세션 BLOB에 직렬화된다) — 이메일이 필요하면
 * {@code CurrentUser.require(...)}로 DB에서 읽는다. 화면은 {@code displayName}을 쓴다.
 * <p>
 * 필드 구성이 바뀌면 이전 세션은 역직렬화되지 않는다(V26이 기존 세션을 비웠다).
 */
public class KraftUserDetails extends org.springframework.security.core.userdetails.User {

    @Serial
    private static final long serialVersionUID = 2L;

    private final Long userId;
    private final String displayName;

    /** @param userId 불변 회원 번호. 로그인 성공 시 세션에도 심어 {@code SessionRevoker}가 대상 계정을 구분한다. */
    public KraftUserDetails(Long userId, String password, String displayName,
                            Collection<? extends GrantedAuthority> authorities) {
        this(userId, password, displayName, true, authorities);
    }

    /** @param accountNonLocked false면 비밀번호를 확인하기도 전에 {@link org.springframework.security.authentication.LockedException}으로 거절된다({@code LoginLockoutService}). */
    public KraftUserDetails(Long userId, String password, String displayName, boolean accountNonLocked,
                            Collection<? extends GrantedAuthority> authorities) {
        super(String.valueOf(userId), password, true, true, true, accountNonLocked, authorities);
        this.userId = userId;
        this.displayName = displayName;
    }

    public Long getUserId() {
        return userId;
    }

    public String getDisplayName() {
        return displayName;
    }
}
