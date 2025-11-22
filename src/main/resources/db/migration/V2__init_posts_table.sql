-- V2: 게시글(posts) 테이블 생성

CREATE TABLE posts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(500) NOT NULL,
    content TEXT NOT NULL,
    author_id BIGINT NOT NULL,
    create_at DATETIME(6) NOT NULL,
    update_at DATETIME(6) NOT NULL,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    PRIMARY KEY (id),
    INDEX idx_post_author_id (author_id),
    INDEX idx_post_created_at (create_at),
    CONSTRAINT fk_post_author FOREIGN KEY (author_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

