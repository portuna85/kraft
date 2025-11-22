-- V6: 댓글 대댓글(답글) 기능 추가

-- parent_id 컬럼 추가 (대댓글인 경우 부모 댓글 ID)
ALTER TABLE comments ADD COLUMN parent_id BIGINT NULL;

-- parent_id 인덱스 추가
CREATE INDEX idx_comment_parent_id ON comments(parent_id);

-- parent_id 외래키 제약조건 추가 (CASCADE DELETE)
ALTER TABLE comments
ADD CONSTRAINT fk_comment_parent
FOREIGN KEY (parent_id)
REFERENCES comments(id)
ON DELETE CASCADE;

