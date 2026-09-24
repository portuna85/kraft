-- 이메일 인증·비밀번호 재설정 토큰을 평문 대신 SHA-512 해시로 저장한다(개선 보고서 SEC-04).
-- 두 표 모두 지금까지 UUID를 평문 그대로 저장해, 백업 유출이나 DB 접근 권한이 있는 사람이
-- 그대로 그 링크를 써서 계정을 가로챌 수 있었다(비밀번호 재설정 토큰은 그 자체로 "새 비밀번호
-- 설정" 권한이다). UUID는 이미 무작위성이 충분해(122비트) 저속 해시가 필요 없다 — 사용자가
-- 고르는 비밀번호와 달리 사전 대입 공격 대상이 아니다.
--
-- SHA2(str, 512)는 애플리케이션의 EmailHasher.sha512Hex와 같은 형식(소문자, 구분자 없는
-- 128자 16진수)을 만든다 — 기존 유효(미만료) 토큰도 해시로 옮겨 무효화하지 않는다.

-- users.email_hash와 같은 VARCHAR(128)을 쓴다 — CHAR(128)은 Hibernate가 매핑하는
-- @Column(length = 128)(varchar)과 타입이 달라 ddl-auto=validate에서 스키마 검증이
-- 실패한다.
ALTER TABLE email_verification_tokens ADD COLUMN token_hash VARCHAR(128) NULL;
UPDATE email_verification_tokens SET token_hash = SHA2(token, 512);
ALTER TABLE email_verification_tokens MODIFY COLUMN token_hash VARCHAR(128) NOT NULL;
ALTER TABLE email_verification_tokens DROP INDEX UK_EMAIL_VERIFICATION_TOKEN;
ALTER TABLE email_verification_tokens DROP COLUMN token;
ALTER TABLE email_verification_tokens
    ADD CONSTRAINT UK_EMAIL_VERIFICATION_TOKEN_HASH UNIQUE (token_hash);

ALTER TABLE password_reset_tokens ADD COLUMN token_hash VARCHAR(128) NULL;
UPDATE password_reset_tokens SET token_hash = SHA2(token, 512);
ALTER TABLE password_reset_tokens MODIFY COLUMN token_hash VARCHAR(128) NOT NULL;
ALTER TABLE password_reset_tokens DROP INDEX UK_PASSWORD_RESET_TOKEN;
ALTER TABLE password_reset_tokens DROP COLUMN token;
ALTER TABLE password_reset_tokens
    ADD CONSTRAINT UK_PASSWORD_RESET_TOKEN_HASH UNIQUE (token_hash);

-- outbox_mails.token은 계속 평문이 필요하다 — 발송 시점에 메일 본문 링크를 만드는 재료라
-- 해시로는 대체할 수 없다(OutboxMail 클래스 주석 참고). 대신 발송이 끝나(SENT/FAILED) 더는
-- 필요 없어지면 애플리케이션이 이 컬럼을 비운다 — 그때까지의 창을 줄이는 정도가 이 표에서
-- 할 수 있는 전부다.
ALTER TABLE outbox_mails MODIFY COLUMN token VARCHAR(100) NULL;

-- 배포 시점에 이미 SENT/FAILED인 행은 이후로 상태가 바뀌지 않아 애플리케이션의
-- markSent/markFailed가 토큰을 비울 기회가 없다 — 마이그레이션이 직접 비운다.
UPDATE outbox_mails SET token = NULL WHERE status IN ('SENT', 'FAILED');
