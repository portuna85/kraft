-- 댓글에 답글(2단계, 대댓글)을 허용한다. 3단계(답글의 답글)는 서비스 계층에서 막는다
-- (CommentService.save). DB 레벨 ON DELETE CASCADE는 일부러 쓰지 않는다 — 이 코드베이스는
-- (comments.deleteAllByPostId처럼) 연쇄 삭제를 항상 애플리케이션 계층의 명시적 벌크 삭제로
-- 처리한다. DB cascade를 쓰면 Flyway 마이그레이션(MariaDB)과 ddl-auto가 만드는 테스트용
-- H2 스키마가 서로 다른 삭제 동작을 갖게 된다 — Hibernate는 @OnDelete 애노테이션이 없으면
-- 이 SQL의 cascade를 모른 채 그냥 FK만 재현하므로, 실제로 H2에서 최상위 댓글 삭제가 FK
-- 위반으로 막히는 것을 겪었다. CommentService.delete()가 답글을 먼저 지운다.
ALTER TABLE comments ADD COLUMN parent_id BIGINT NULL;
ALTER TABLE comments ADD CONSTRAINT FK_COMMENTS_PARENT FOREIGN KEY (parent_id) REFERENCES comments (id);
CREATE INDEX IX_COMMENTS_PARENT ON comments (parent_id);
