-- 로그인 무차별 대입에 대한 계정 단위 누적 방어(전체 리뷰 2026-09-26 A-SEC-08). 지금까지는
-- 분당 요청 횟수만 막아(AuthRateLimitFilter), IP를 바꾸면 하루 14,400회까지 시도할 수 있었다.
-- 연속 실패를 계정에 누적해 5회부터 지수 백오프로 잠근다.
ALTER TABLE users ADD COLUMN failed_login_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN locked_until DATETIME(6) NULL;

-- 연속 실패가 임계를 넘으면 계정 주인에게 메일로 알린다(LoginLockoutService). 값 목록은
-- Hibernate가 만드는 것과 같은 알파벳 순이다(V8 주석 참고) — LOGIN_ATTEMPTS_WARNING이
-- PASSWORD_RESET보다 앞선다.
ALTER TABLE outbox_mails MODIFY COLUMN kind
    ENUM('LOGIN_ATTEMPTS_WARNING','PASSWORD_RESET','VERIFY_EMAIL') NOT NULL;
