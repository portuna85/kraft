-- Spring Session JDBC의 세션 테이블.
--
-- application.yml이 모든 프로파일에 spring.session.store-type: jdbc를 강제하는데, 운영은
-- spring.session.jdbc.initialize-schema: never라 Spring Session이 테이블을 만들지 않는다.
-- 이 테이블들은 JPA 엔티티가 아니라 ddl-auto: validate도 부재를 잡아내지 못해서, 없으면
-- "기동은 성공하고 첫 로그인에서 Table 'SPRING_SESSION' doesn't exist"로 터진다.
-- 그래서 운영 스키마를 관리하는 Flyway가 직접 만들도록 여기에 둔다.
--
-- IF NOT EXISTS인 이유: 세션 테이블은 앱 스키마 버전 이력과 무관하게, 과거 배포에서
-- Spring Session의 자체 초기화(initialize-schema: always)로 이미 만들어져 있을 수 있다.
-- baseline-on-migrate로 기존 DB에 처음 붙는 경우에도 안전하게 넘어가야 한다.
--
-- local은 Flyway를 쓰지 않으므로 같은 내용을 src/main/resources/session/schema-mariadb.sql로
-- 따로 두고 spring.session.jdbc.schema로 가리킨다. 둘 중 하나를 고치면 다른 쪽도 함께 고칠 것.

CREATE TABLE IF NOT EXISTS SPRING_SESSION (
	PRIMARY_ID CHAR(36) NOT NULL,
	SESSION_ID CHAR(36) NOT NULL,
	CREATION_TIME BIGINT NOT NULL,
	LAST_ACCESS_TIME BIGINT NOT NULL,
	MAX_INACTIVE_INTERVAL INT NOT NULL,
	EXPIRY_TIME BIGINT NOT NULL,
	PRINCIPAL_NAME VARCHAR(100),
	-- MariaDB는 PRIMARY KEY에 붙인 이름을 무시하면서 WARN(1280)을 남긴다. 이름은 어차피
	-- 쓰이지 않으므로 생략한다(session/schema-mariadb.sql과의 유일한 차이).
	PRIMARY KEY (PRIMARY_ID)
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC;

CREATE UNIQUE INDEX IF NOT EXISTS SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX IF NOT EXISTS SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX IF NOT EXISTS SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);

CREATE TABLE IF NOT EXISTS SPRING_SESSION_ATTRIBUTES (
	SESSION_PRIMARY_ID CHAR(36) NOT NULL,
	ATTRIBUTE_NAME VARCHAR(200) NOT NULL,
	ATTRIBUTE_BYTES BLOB NOT NULL,
	PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
	CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID) REFERENCES SPRING_SESSION(PRIMARY_ID) ON DELETE CASCADE
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC;
