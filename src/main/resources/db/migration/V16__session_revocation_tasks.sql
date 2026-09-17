-- 비밀번호 변경·재설정·탈퇴 뒤 세션 폐기는 커밋 후 콜백(AfterCommit)에서 한 번 시도됐다. 그
-- 시도가 실패하거나(세션 저장소 장애 등) 그 직후 프로세스가 죽으면, DB 변경(비밀번호 등)은
-- 이미 커밋됐는데 세션 폐기만 유실되어 복구할 방법이 없었다(개선 보고서 "세션 폐기 실패를
-- 복구할 지속 상태 부재"). 이 테이블이 그 시도를 영속 태스크로 남겨, 실패해도 주기 작업이
-- 다시 집어 최종적으로 완수하게 한다. outbox_mails와 같은 claim/재시도 구조를 그대로 쓴다.
--
-- email_snapshot은 태스크를 만든 시점(트랜잭션 커밋 시점)의 이메일을 그대로 저장한다. 탈퇴는
-- users.email을 익명 주소로 바꾸므로, 태스크 처리 시점에 user_id로 다시 조회하면 이미 다른
-- 값을 얻는다. 이 값으로 고정해 두어야 탈퇴 후 같은 이메일로 재가입한 새 계정의 세션을
-- 잘못 지우지 않는다. users.email과 같은 방식(AES, EmailAttributeConverter)으로 암호화한다.
CREATE TABLE session_revocation_tasks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    user_id BIGINT NOT NULL,
    email_snapshot VARCHAR(500) NOT NULL,
    status ENUM('DONE','FAILED','PENDING','PROCESSING') NOT NULL,
    attempts INT NOT NULL,
    last_error VARCHAR(500),
    completed_at DATETIME(6),
    owner_token VARCHAR(36),
    PRIMARY KEY (id),
    CONSTRAINT FK_SESSION_REVOCATION_TASKS_USER FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB;

-- 작업자가 "처리할 것"을 id 순서로 집는다.
CREATE INDEX IX_SESSION_REVOCATION_TASKS_STATUS_ID ON session_revocation_tasks (status, id);

-- 처리 도중 중단된 것(PROCESSING인 채 오래 남은 것)을 되돌릴 때 훑는다.
CREATE INDEX IX_SESSION_REVOCATION_TASKS_STATUS_UPDATED ON session_revocation_tasks (status, updated_at);
