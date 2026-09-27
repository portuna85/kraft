-- 가입 계정 열거 방지(전체 리뷰 2026-09-26 A-SEC-01). 이미 가입된 이메일로 다시 가입을
-- 시도해도 신규 가입과 같은 응답을 주고, 대신 계정 주인에게만 보이는 안내 메일을 보낸다.
-- 값 목록은 Hibernate가 만드는 것과 같은 알파벳 순이다(V8 주석 참고) — ACCOUNT_EXISTS가
-- LOGIN_ATTEMPTS_WARNING보다 앞선다.
ALTER TABLE outbox_mails MODIFY COLUMN kind
    ENUM('ACCOUNT_EXISTS','LOGIN_ATTEMPTS_WARNING','PASSWORD_RESET','VERIFY_EMAIL') NOT NULL;
