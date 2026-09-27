package com.kraft.user.mail;

/**
 * 대기열에 담긴 메일의 종류. 제목과 본문을 정하는 유일한 근거다({@link OutboxMailWorker}).
 * <p>
 * 수신 주소도 본문도 컬럼에 담지 않기 때문에(V7 주석 참고) 보낼 때 다시 만들어야 하고,
 * 그러려면 "무엇을 보내려던 행인지"가 남아 있어야 한다.
 * <p>
 * 값 목록은 Hibernate가 MariaDB에 만드는 네이티브 ENUM과 같도록 알파벳 순으로 쓴다.
 */
public enum OutboxMailKind {

    /**
     * 로그인 연속 실패가 임계를 넘었다는 알림(전체 리뷰 2026-09-26 A-SEC-08). 링크·토큰이
     * 없다 — {@code OutboxMail.token}은 이 종류에서 항상 null이다.
     */
    LOGIN_ATTEMPTS_WARNING,

    /** 비밀번호 재설정 링크. 30분짜리 1회용이다. */
    PASSWORD_RESET,

    /** 가입 직후·재발송의 이메일 인증 링크. */
    VERIFY_EMAIL
}
