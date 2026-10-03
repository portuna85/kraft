-- 목록은 sort=updatedAt(최근 수정순)을 허용하는데 updated_at에 인덱스가 없어, 정렬할 때마다
-- 테이블 전체를 읽어 filesort 했다(BE-07). 기본 정렬(최신순, id DESC)과 같은 방향으로
-- (updated_at DESC, id DESC)를 두면 LIMIT만큼만 읽고 멈춘다. PostSortPolicy가 동점 처리로
-- id DESC를 덧붙이므로 이 모양이 그대로 쓰인다.
-- 분류 필터와 함께 쓰는 경우를 위해 (category, updated_at DESC, id DESC)도 둔다.
-- 추가만 하는 마이그레이션이라 이전 jar가 떠 있는 동안에도 안전하다(확장→배포).
CREATE INDEX IX_POSTS_UPDATED_AT_ID ON posts (updated_at DESC, id DESC);
CREATE INDEX IX_POSTS_CATEGORY_UPDATED_AT_ID ON posts (category, updated_at DESC, id DESC);
