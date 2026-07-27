-- Phase 0 초안(검토용, 미적용) — docs/improvement.md §14.2.
-- §14.3 순서의 1단계(nullable 컬럼 추가)까지만 다룬다. "익명 또는 사용자 소유권 중
-- 하나만 유효"하도록 하는 CHECK 제약과 사용자별 정규화 번호 유일성 제약은 기존 행
-- 보정(2단계) 이후 별도 마이그레이션에서 추가한다(6번, 제약 조건 강화 단계).

ALTER TABLE saved_numbers
    ADD COLUMN owner_user_id BIGINT NULL AFTER id,
    ADD CONSTRAINT fk_saved_numbers_owner FOREIGN KEY (owner_user_id)
        REFERENCES community_users (id) ON DELETE RESTRICT;

CREATE INDEX idx_saved_numbers_owner_created ON saved_numbers (owner_user_id, created_at DESC);

-- §14.2: category, status, recommendation_set_id nullable 추가. 기존 글은 category='GENERAL'로
-- 보정한다(§11.1) — 이 파일은 컬럼 추가까지만 다루고, 기존 행 백필은 §14.3 2단계에서
-- 별도 DML로 수행한다(대량 UPDATE는 스키마 마이그레이션과 분리해 잠금 시간을 따로 측정).
ALTER TABLE community_posts
    ADD COLUMN category VARCHAR(30) NULL AFTER title,
    ADD COLUMN status VARCHAR(20) NULL AFTER category,
    ADD COLUMN recommendation_set_id BIGINT NULL AFTER status,
    ADD CONSTRAINT fk_community_posts_recommendation_set FOREIGN KEY (recommendation_set_id)
        REFERENCES recommendation_sets (id) ON DELETE SET NULL;

CREATE INDEX idx_community_posts_category_created ON community_posts (category, created_at DESC);
CREATE INDEX idx_community_posts_status ON community_posts (status);
