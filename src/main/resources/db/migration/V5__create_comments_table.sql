-- V5: 댓글 테이블 생성

CREATE TABLE comments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    content TEXT NOT NULL,
    post_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    create_at DATETIME(6) NOT NULL,
    update_at DATETIME(6) NOT NULL,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    PRIMARY KEY (id),
    INDEX idx_comment_post_id (post_id),
    INDEX idx_comment_author_id (author_id),
    INDEX idx_comment_created_at (create_at),
    CONSTRAINT fk_comment_post FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE,
    CONSTRAINT fk_comment_author FOREIGN KEY (author_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

