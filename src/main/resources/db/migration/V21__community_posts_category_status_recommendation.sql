-- 기존 글은 DEFAULT로 GENERAL/PUBLISHED로 자동 보정된다(문서 11.1/11.2).
-- recommendation_set_id는 게시글에 첨부된 추천 세트를 가리킨다 — 세트 자체는 append-only라
-- 별도 스냅샷 복제 없이 FK 참조만으로 불변성이 보장된다(추천 세트 삭제는 첨부된 게시글이
-- 있으면 애플리케이션 레벨에서 거부한다. RESTRICT로 DB 레벨에서도 이중 방어한다).
ALTER TABLE community_posts
    ADD COLUMN category VARCHAR(30) NOT NULL DEFAULT 'GENERAL',
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED',
    ADD COLUMN recommendation_set_id BIGINT NULL,
    ADD CONSTRAINT fk_community_posts_recommendation_set FOREIGN KEY (recommendation_set_id)
        REFERENCES recommendation_sets (id) ON DELETE RESTRICT;

CREATE INDEX idx_community_posts_category_status_created ON community_posts (category, status, created_at);
