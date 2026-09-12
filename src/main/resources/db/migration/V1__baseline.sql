-- 이번 Flyway 도입 이전까지 존재하던 스키마(사용자·게시글·댓글·이메일 인증 토큰)를
-- 기준선으로 만든다. 이미 스키마가 있는 기존 운영 DB에서는
-- (spring.flyway.baseline-on-migrate=true, baseline-version=1 설정으로) 이 파일이 실제로
-- 실행되지 않고 "이미 적용됨"으로만 표시된다. 완전히 새로 만드는 DB에서만 실제로 실행된다.
--
-- 로컬(MariaDB, ddl-auto: update)·테스트(H2, create-drop) 프로파일은 Flyway를 쓰지 않고 Hibernate가
-- 엔티티 매핑으로 스키마를 직접 만들므로, 이 파일은 운영(MariaDB)에서만 실행 대상이다.

CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    name VARCHAR(50) NOT NULL,
    email VARCHAR(500) NOT NULL,
    email_hash VARCHAR(128) NOT NULL,
    password VARCHAR(100) NOT NULL,
    -- Hibernate는 MariaDB에서 @Enumerated(STRING)을 네이티브 ENUM으로 만들고 기대한다
    -- (logs/kraft-sql.log의 create table users: `role enum ('ADMIN','GUEST','USER') not null`).
    -- VARCHAR로 두면 운영의 ddl-auto: validate가 타입 불일치로 기동을 막는다. 값 목록은
    -- Hibernate가 만드는 것과 똑같이 알파벳 순(선언 순서가 아님)으로 맞춘다.
    role ENUM('ADMIN','GUEST','USER') NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT UK_USER_EMAIL_HASH UNIQUE (email_hash)
) ENGINE=InnoDB;

CREATE TABLE posts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    picture VARCHAR(500),
    user_id BIGINT,
    PRIMARY KEY (id),
    CONSTRAINT FK_POSTS_USER FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE TABLE comments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    content TEXT NOT NULL,
    post_id BIGINT,
    user_id BIGINT,
    PRIMARY KEY (id),
    CONSTRAINT FK_COMMENTS_POST FOREIGN KEY (post_id) REFERENCES posts (id),
    CONSTRAINT FK_COMMENTS_USER FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE TABLE email_verification_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    expires_at DATETIME(6) NOT NULL,
    token VARCHAR(100) NOT NULL,
    user_id BIGINT,
    PRIMARY KEY (id),
    CONSTRAINT UK_EMAIL_VERIFICATION_TOKEN UNIQUE (token),
    CONSTRAINT FK_EVT_USER FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB;
