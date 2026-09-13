-- 계정별 이미지 저장량 계산에 쓸 파일 크기와, 목록·검색·인기글 조회용 인덱스.

-- 파일 하나당 5MB 제한만으로는 반복 업로드로 디스크를 채우는 것을 막을 수 없다.
-- 계정별 누적 저장량을 합산하려면 파일 크기를 기록해야 한다(개선 보고서 F06).
-- 기존 행은 크기를 알 수 없으므로 0으로 채운다 — 그만큼 한도를 덜 쓴 것으로 계산된다.
ALTER TABLE post_images
    ADD COLUMN size_bytes BIGINT NOT NULL DEFAULT 0;

-- 저장량 합산은 owner_id로 훑는다.
CREATE INDEX IX_POST_IMAGES_OWNER ON post_images (owner_id);

-- 목록은 항상 id DESC 정렬이고 분류로 좁힌다. 인기글은 view_count DESC 정렬이다.
-- 검색어(LIKE '%keyword%')는 선행 와일드카드라 B-tree 인덱스를 탈 수 없다 — 데이터가 늘면
-- 전문검색(FULLTEXT)이나 별도 검색 인덱스를 검토해야 하며, 이 인덱스로 해결되지 않는다.
CREATE INDEX IX_POSTS_CATEGORY_ID ON posts (category, id);
CREATE INDEX IX_POSTS_VIEW_COUNT ON posts (view_count DESC, id DESC);

-- 목록의 댓글 수 집계(countByPostIdIn)와 게시글 삭제 시의 일괄 삭제가 post_id로 훑는다.
CREATE INDEX IX_COMMENTS_POST ON comments (post_id);

-- 만료 토큰 정리 배치가 expires_at으로 훑는다.
CREATE INDEX IX_EVT_EXPIRES_AT ON email_verification_tokens (expires_at);
