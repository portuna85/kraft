package com.kraft.user.mail;

/**
 * 대기열 메일의 종류 — 제목과 본문을 정하는 유일한 근거다({@link OutboxMailWorker}). 수신 주소·본문을 컬럼에 담지 않아(V7)
 * 보낼 때 다시 만들어야 한다. 값은 Hibernate가 만드는 네이티브 ENUM과 같게 알파벳 순이다.
 */
public enum OutboxMailKind {

    /** 이미 가입된 이메일로 다시 가입을 시도했다는 안내(계정 열거 방지로 가입 API 응답은 같다). 링크·토큰이 없어 {@code OutboxMail.token}은 항상 null이다. */
    ACCOUNT_EXISTS,

    /** 로그인 연속 실패가 임계를 넘었다는 알림. 링크·토큰이 없다. */
    LOGIN_ATTEMPTS_WARNING,

    /** 비밀번호 재설정 링크. 30분짜리 1회용이다. */
    PASSWORD_RESET,

    /** 가입 직후·재발송의 이메일 인증 링크. */
    VERIFY_EMAIL
}
