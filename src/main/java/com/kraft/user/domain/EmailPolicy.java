package com.kraft.user.domain;

/**
 * 지원하는 이메일 주소 길이 정책.
 * <p>
 * 이 값 하나에 세 군데의 경계가 걸려 있다. 예전에는 셋이 서로 달라서, 어디까지가 "가입 가능한
 * 이메일"인지가 입력 검증이 아니라 우연히 먼저 터지는 저장소에 의해 정해졌다:
 * <ol>
 * <li><b>입력 검증</b>({@code SignUpRequestDto}) — 길이 제한이 아예 없었다.</li>
 * <li><b>암호화 저장 컬럼</b>({@code users.email VARCHAR(500)}) — {@link EmailAttributeConverter}가
 * AES-GCM 암호문을 hex로 저장해 길이가 {@code 평문 × 2 + 64}가 된다. 즉 218자까지만 들어간다.
 * 219자 이메일은 DTO 검증을 통과하고 DB 저장에서 실패했다.</li>
 * <li><b>세션 principal</b>({@code SPRING_SESSION.PRINCIPAL_NAME VARCHAR(100)}) — 로그인
 * 식별자가 이메일 전체이므로 여기에도 담겨야 한다. 이 100자는 Kraft가 정한 값이 아니라
 * spring-session-jdbc가 번들하는 {@code schema-h2.sql}·{@code schema-mysql.sql}의 기본값이다.</li>
 * </ol>
 * 셋 중 가장 좁은 경계가 100자이므로 그것을 제품 정책으로 삼는다. 가입 시점에 명확히 거부하면,
 * "가입은 됐는데 로그인할 수 없는 계정"(101~218자)이 만들어지지 않는다.
 * <p>
 * RFC 5321이 허용하는 최대치는 254자다. 그보다 좁은 정책을 택한 이유는 프레임워크 기본 스키마를
 * 그대로 쓰기 위해서다. 254자를 지원해야 한다면 이 상수와 함께 위 2·3의 컬럼을 모두 넓히고,
 * 운영 마이그레이션·local 세션 SQL·테스트용 H2 스키마 세 곳을 같이 고쳐야 한다.
 * <p>
 * 참고: 전체 길이와 별개로 {@code @Email}이 local part를 64자, 도메인 라벨 하나를 63자로
 * 제한한다(RFC 5321). 이 상수는 그 위에 얹히는 <b>전체 길이</b> 제한이다.
 */
public final class EmailPolicy {

    /** 지원하는 이메일 주소의 최대 길이(문자 수). */
    public static final int MAX_LENGTH = 100;

    private EmailPolicy() {
    }
}
