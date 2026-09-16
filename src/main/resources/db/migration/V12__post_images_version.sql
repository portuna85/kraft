-- post_images에 낙관적 잠금 버전을 추가한다.
--
-- 첨부(attach)는 조회 후 상태만 바꾸는 방식이라 잠금이 없었다. 같은 미연결 이미지를 서로
-- 다른 두 게시글이 거의 동시에 붙이면 둘 다 "아직 다른 글에 붙지 않았다"는 검사를 통과해,
-- 나중에 커밋한 쪽이 먼저 커밋한 연결을 조용히 덮어썼다(개선 보고서 "이미지 첨부 상태 경쟁").
--
-- posts.version(V4)과 같은 이유로 NOT NULL DEFAULT 0으로 둔다 — @Version 컬럼이 null을
-- 허용하면 Hibernate가 기존 행을 "분리된 엔티티"로 오해할 수 있다.
ALTER TABLE post_images
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
