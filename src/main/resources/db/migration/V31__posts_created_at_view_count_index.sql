-- 인기글이 누적 조회수 대신 "최근 7일 내 작성 글 중 조회수 상위"로 바뀌면서(전체 리뷰
-- 2026-09-26 A-BE-10) created_at 조건 + view_count 정렬을 함께 쓰는 쿼리가 생겼다.
CREATE INDEX IX_POSTS_CREATED_AT_VIEW_COUNT ON posts (created_at, view_count DESC, id DESC);
