package com.kraft.user.domain;

import java.util.Locale;

/**
 * 이메일 주소 길이 정책과 정규화.
 * <p>
 * 길이 경계는 세 곳에 걸려 있다: 입력 검증({@code SignUpRequestDto}), 암호화 저장 컬럼({@code users.email VARCHAR(500)} —
 * {@link EmailAttributeConverter}의 hex 암호문이 평문 × 2 + 64라 218자까지), 세션 principal
 * ({@code SPRING_SESSION.PRINCIPAL_NAME VARCHAR(100)} — spring-session-jdbc 기본 스키마). 가장 좁은 100자를 정책으로
 * 삼아 가입 시점에 거부한다(아니면 "가입은 됐는데 로그인할 수 없는 계정"이 생긴다). RFC 5321의 254자를 지원하려면 이
 * 상수와 함께 위 두 컬럼(운영 마이그레이션·local 세션 SQL·테스트 H2 스키마)을 모두 넓혀야 한다. {@code @Email}의
 * local part 64자·도메인 라벨 63자 제한은 별개다.
 * <p>
 * {@link #normalize(String)}는 해시·비교 전에 항상 거치는 정규화다(대소문자만 다른 중복 계정과 로그인 실패를 막는다).
 * 이메일을 해시하거나 비교하는 모든 진입점이 거쳐야 하며, 이미 정규화된 {@code email} 필드를 쓰는
 * {@code User.hashEmail()} 같은 엔티티 훅은 예외다.
 */
public final class EmailPolicy {

    /** 지원하는 이메일 주소의 최대 길이(문자 수). */
    public static final int MAX_LENGTH = 100;

    private EmailPolicy() {
    }

    public static String normalize(String email) {
        if (email == null) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
