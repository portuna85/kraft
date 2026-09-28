-- 상세 화면이 <img width height>로 레이아웃 이동(CLS)을 줄이도록 첨부 이미지의 실제
-- 픽셀 크기를 저장한다(A-FE-09). 확장 전용이라 옛 jar로 롤백해도 문제없다 — 이 두 컬럼을
-- 모르는 쿼리는 그냥 무시한다(db/migration/README.md의 확장→배포→수축 규칙).
--
-- 이미 저장된 글은 두 값이 NULL로 남는다(소급 채움 없음) — 화면은 NULL이면 width/height
-- 속성을 생략하는 이전 동작으로 자연히 물러난다.
ALTER TABLE posts ADD COLUMN picture_width INT NULL;
ALTER TABLE posts ADD COLUMN picture_height INT NULL;
