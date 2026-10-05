-- 업로드 쿼터 합계(SUM(size_bytes) WHERE owner_id = ? AND status <> 'PENDING_DELETE')가 owner_id 단일
-- 인덱스를 타면 행마다 테이블을 다시 읽는다. 세 컬럼을 모두 담은 커버링 인덱스로 인덱스만 훑게 한다.
-- 기존 IX_POST_IMAGES_OWNER는 FK가 쓸 수 있어 지우지 않는다(중복이지만 행이 적고 해가 없다).
CREATE INDEX IX_POST_IMAGES_OWNER_STATUS_SIZE ON post_images (owner_id, status, size_bytes);
