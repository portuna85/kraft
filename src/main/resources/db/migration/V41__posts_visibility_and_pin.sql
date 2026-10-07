-- 게시글 소프트 삭제(deleted_at)·관리자 숨김(blinded_at)·기한 고정(pinned_until)과 댓글 숨김.
-- 모두 NULL 허용 타임스탬프라 기존 행은 "보이는 글·고정 안 됨"으로 남고, MariaDB가 INSTANT로 추가한다.
ALTER TABLE posts
    ADD COLUMN deleted_at   DATETIME(6) NULL,
    ADD COLUMN blinded_at   DATETIME(6) NULL,
    ADD COLUMN pinned_until DATETIME(6) NULL;

ALTER TABLE comments
    ADD COLUMN blinded_at DATETIME(6) NULL;

-- 목록 COUNT(deleted_at IS NULL AND blinded_at IS NULL)가 테이블 행을 읽지 않고 인덱스만 훑게 한다.
-- 분류별 목록은 (category, ...)로 같은 일을 한다. InnoDB가 PK(id)를 덧붙여 id 순으로도 읽힌다.
CREATE INDEX IX_POSTS_VISIBLE ON posts (deleted_at, blinded_at);
CREATE INDEX IX_POSTS_CATEGORY_VISIBLE ON posts (category, deleted_at, blinded_at);
CREATE INDEX IX_POSTS_PINNED_UNTIL ON posts (pinned_until);
