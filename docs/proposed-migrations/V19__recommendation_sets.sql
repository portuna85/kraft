-- Phase 0 초안(검토용, 미적용) — docs/improvement.md §9.4, §14.1.
-- 익명 상태에서는 client_token_hash, 로그인 상태에서는 owner_user_id가 소유권을 나타낸다.
-- 둘 다 없는 행과 둘 다 활성 소유권인 행을 금지한다(§9.4) — 이 CHECK 제약은 §14.3의
-- "제약 조건 강화" 단계(6번)에 해당하므로 실제 적용 시에는 별도 마이그레이션으로 분리하고,
-- 여기서는 스키마 형태만 초안으로 둔다.
CREATE TABLE recommendation_sets (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_user_id BIGINT NULL,
    client_token_hash CHAR(64) NULL,
    strategy VARCHAR(40) NOT NULL,
    algorithm_version VARCHAR(40) NOT NULL,
    history_through_round INT NOT NULL,
    locked_numbers VARCHAR(32) NOT NULL DEFAULT '',
    excluded_numbers VARCHAR(160) NOT NULL DEFAULT '',
    created_at DATETIME(6) NOT NULL,
    claimed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_recommendation_sets_owner FOREIGN KEY (owner_user_id)
        REFERENCES community_users (id) ON DELETE RESTRICT,
    KEY idx_recommendation_sets_owner_created (owner_user_id, created_at DESC),
    KEY idx_recommendation_sets_client_created (client_token_hash, created_at DESC)
);

-- 추천 공유 이후 알고리즘이 바뀌어도 게시글 카드가 변하지 않도록 생성 당시 결과를
-- 불변으로 보존한다(§9.4).
CREATE TABLE recommendation_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    set_id BIGINT NOT NULL,
    position INT NOT NULL,
    numbers VARCHAR(32) NOT NULL,
    score DECIMAL(10, 4) NULL,
    explanation_codes VARCHAR(200) NOT NULL DEFAULT '',
    PRIMARY KEY (id),
    CONSTRAINT fk_recommendation_items_set FOREIGN KEY (set_id)
        REFERENCES recommendation_sets (id) ON DELETE CASCADE,
    UNIQUE KEY uk_recommendation_items_set_position (set_id, position)
);
