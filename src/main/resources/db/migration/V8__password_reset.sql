-- 비밀번호 찾기(재설정).
--
-- 지금까지 비밀번호를 잊으면 되돌릴 방법이 없었다. 로그인해야 바꿀 수 있는데, 잊은 사람은
-- 로그인할 수 없다. 이메일로 1회용 링크를 보내 그 고리를 끊는다.
--
-- 인증 토큰(email_verification_tokens)과 같은 모양이지만 표를 나눈다. 수명(30분 대 24시간)과
-- 의미가 다르고, 한쪽을 지우는 일이 다른 쪽에 영향을 주면 안 되기 때문이다.
CREATE TABLE password_reset_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    token VARCHAR(100) NOT NULL,
    user_id BIGINT,
    expires_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT UK_PASSWORD_RESET_TOKEN UNIQUE (token),
    CONSTRAINT FK_PASSWORD_RESET_TOKENS_USER FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB;

-- 재발급할 때 이 회원의 옛 토큰을 지운다.
CREATE INDEX IX_PASSWORD_RESET_TOKENS_USER ON password_reset_tokens (user_id);

-- 대기열은 이제 두 종류의 메일을 나른다. 어느 쪽인지 알아야 제목과 본문을 정할 수 있다.
-- ENUM으로 두는 이유는 V1의 users.role 주석 참고. 값 목록은 Hibernate가 만드는 것과 같은
-- 알파벳 순이다. 이미 쌓여 있는 행은 모두 이메일 인증 메일이므로 기본값이 그대로 맞다.
ALTER TABLE outbox_mails
    ADD COLUMN kind ENUM('PASSWORD_RESET','VERIFY_EMAIL') NOT NULL DEFAULT 'VERIFY_EMAIL';
