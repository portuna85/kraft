-- IX_POST_IMAGES_OWNER(owner_id)는 IX_POST_IMAGES_OWNER_STATUS_SIZE(owner_id, status, size_bytes)의 왼쪽 접두사라
-- FK(owner_id)와 소유자 조회를 모두 그쪽 인덱스가 맡는다.
DROP INDEX IX_POST_IMAGES_OWNER ON post_images;
