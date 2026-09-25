package com.kraft.config.security;

import org.springframework.security.core.GrantedAuthority;

import java.io.Serial;
import java.util.Collection;

/**
 * 화면에 보여줄 이름(가입 시 입력한 닉네임)을 함께 들고 다니는 {@link org.springframework.security.core.userdetails.UserDetails}.
 * <p>
 * 로그인 아이디({@code getUsername()}, 곧 {@code Authentication.getName()})는 <b>불변 회원
 * 번호(id)의 문자열</b>이다(BE-04) — 예전에는 이메일을 그대로 썼는데, Spring Session JDBC가
 * {@code SPRING_SESSION.PRINCIPAL_NAME}과 직렬화된 SecurityContext에 이 값을 평문으로 저장해
 * {@code User.email}의 컬럼 암호화(EmailAttributeConverter)를 우회하는 경로가 됐다.
 * <p>
 * 같은 이유로 <b>이메일을 필드로 들고 다니지 않는다</b>(평가 보고서 2026-09-25 F01) — 이 객체
 * 전체가 {@code SPRING_SESSION_ATTRIBUTES.ATTRIBUTE_BYTES}에 직렬화되므로, 필드로 두면 principal
 * 이름을 id로 바꾼 것과 무관하게 세션 BLOB에 이메일 원문이 남았다. 이메일이 필요한 호출부는
 * {@code CurrentUser.require(...)}로 DB에서 회원을 읽어 쓴다. 화면에는 {@code displayName}을
 * 보여준다({@code sec:authentication="principal.displayName"}).
 * <p>
 * 필드 구성이 바뀌면 이전에 직렬화된 세션은 역직렬화되지 않는다 — 이 변경과 함께 V26 마이그레이션이
 * 기존 세션을 모두 비운다(전원 1회 재로그인).
 */
public class KraftUserDetails extends org.springframework.security.core.userdetails.User {

    @Serial
    private static final long serialVersionUID = 2L;

    private final Long userId;
    private final String displayName;

    /**
     * @param userId 불변 회원 번호(B02). 로그인 성공 시 세션에도 심어 {@code SessionRevoker}가
     *               대상 계정을 구분한다({@code config.security.SecurityConfig#redirectAwareSuccessHandler} 참고).
     */
    public KraftUserDetails(Long userId, String password, String displayName,
                            Collection<? extends GrantedAuthority> authorities) {
        super(String.valueOf(userId), password, authorities);
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
