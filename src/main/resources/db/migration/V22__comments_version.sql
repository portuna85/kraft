-- 두 탭에서 같은 댓글을 동시에 저장하면 나중에 flush되는 쪽이 앞선 내용을 조용히 덮어썼다
-- (B12). Post.version과 같은 이유로 낙관적 잠금 버전을 둔다 — 클라이언트가 편집 화면을 받아간
-- 시점의 버전을 저장 요청에 함께 보내면, 그 사이 다른 저장이 있었을 때 409로 충돌을 표면화한다.
--
-- 기존 행은 0부터 시작한다. 저장된 데이터·현재 버전이 바뀌는 컬럼이 아니므로 별도 백필이
-- 필요 없다(V17__users_version.sql과 같은 방식).
ALTER TABLE comments ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
