-- V4: 게시글 조회수 컬럼 추가

ALTER TABLE posts ADD COLUMN view_count BIGINT NOT NULL DEFAULT 0;

-- 조회수 인덱스 추가 (인기 게시글 정렬용)
CREATE INDEX idx_post_view_count ON posts(view_count DESC);

