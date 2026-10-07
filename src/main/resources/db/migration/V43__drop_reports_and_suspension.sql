-- 수축(contract): 신고·정지 기능을 걷어낸 배포(478624d)가 운영에서 안정적으로 돈 뒤 그 흔적을 지운다.
-- 애플리케이션은 이미 reports 테이블과 users.suspended_until / suspension_reason을 읽지도 쓰지도 않는다.
-- 이 마이그레이션이 적용된 뒤에는 그 배포 이전의 jar로 롤백할 수 없다(db/migration/README.md).
--
-- IF EXISTS를 쓴다 — ddl-auto로 만든 개발 DB에는 처음부터 이 테이블·컬럼이 없다.
DROP TABLE IF EXISTS reports;

DROP INDEX IF EXISTS IX_USERS_SUSPENDED_UNTIL ON users;
ALTER TABLE users DROP COLUMN IF EXISTS suspended_until;
ALTER TABLE users DROP COLUMN IF EXISTS suspension_reason;
