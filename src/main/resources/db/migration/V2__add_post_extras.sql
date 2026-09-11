-- 검색·분류·조회수·추천(좋아요)·인기글·댓글수 기능에 필요한 스키마 변경.
-- category는 기존 게시글에도 기본값 FREE(자유)로 채워지고, view_count는 0부터 시작한다.

ALTER TABLE posts
    ADD COLUMN category VARCHAR(20) NOT NULL DEFAULT 'FREE',
    ADD COLUMN view_count BIGINT NOT NULL DEFAULT 0;

CREATE TABLE post_likes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    post_id BIGINT,
    user_id BIGINT,
    PRIMARY KEY (id),
    CONSTRAINT UK_POST_LIKE_POST_USER UNIQUE (post_id, user_id),
    CONSTRAINT FK_POST_LIKES_POST FOREIGN KEY (post_id) REFERENCES posts (id),
    CONSTRAINT FK_POST_LIKES_USER FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB;
