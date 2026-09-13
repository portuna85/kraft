-- 보낼 메일을 적어 두는 대기열.
--
-- 예전에는 회원가입 트랜잭션 안에서 SMTP를 그대로 호출했다. 연결·읽기·쓰기 타임아웃이 각각
-- 5초라 메일 서버가 굼뜨면 DB 커넥션 하나를 최대 15초 붙잡은 채 기다렸고, 동시에 몇 명만
-- 가입해도 커넥션 풀이 말랐다(개선 보고서 "메일 안정성").
--
-- 이제 트랜잭션 안에서는 이 행만 만들고, 실제 발송은 OutboxMailWorker가 트랜잭션 밖에서 한다.
-- 덤으로 재시도 횟수와 실패 원인이 남는다.
--
-- 수신 주소와 본문을 컬럼에 담지 않는 것은 의도다. 이메일은 users.email에 암호화해 저장하는데
-- 여기에 평문으로 또 적으면 그 보호가 무의미해진다. 회원과 토큰만 가리키고 보낼 때 만든다.

-- status를 ENUM으로 두는 이유는 V1의 users.role 주석 참고(Hibernate가 MariaDB에서 네이티브
-- ENUM을 기대하므로 VARCHAR면 ddl-auto: validate가 기동을 막는다).
-- 값 목록은 Hibernate가 만드는 것과 같은 알파벳 순이다.
CREATE TABLE outbox_mails (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    user_id BIGINT NOT NULL,
    token VARCHAR(100) NOT NULL,
    status ENUM('FAILED','PENDING','SENDING','SENT') NOT NULL,
    attempts INT NOT NULL,
    last_error VARCHAR(500),
    sent_at DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT FK_OUTBOX_MAILS_USER FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB;

-- 작업자가 "보낼 것"을 id 순서로 집는다.
CREATE INDEX IX_OUTBOX_MAILS_STATUS_ID ON outbox_mails (status, id);

-- 발송 도중 중단된 것(SENDING인 채 오래 남은 것)을 되돌릴 때 훑는다.
CREATE INDEX IX_OUTBOX_MAILS_STATUS_UPDATED ON outbox_mails (status, updated_at);

-- 재발송 요청 제한이 "이 회원의 마지막 메일"을 찾는다.
CREATE INDEX IX_OUTBOX_MAILS_USER ON outbox_mails (user_id, id);
