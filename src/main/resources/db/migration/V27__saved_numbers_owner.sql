-- 로그인 계정 귀속(Phase 4) — client_token_hash(익명)와 owner_user_id(계정) 중 정확히
-- 하나만 채워지는 상호 배타 소유권으로 확장한다(문서 10.3). 기존 행은 client_token_hash가
-- 그대로 남아 자동 호환된다.
ALTER TABLE saved_numbers
    MODIFY COLUMN client_token_hash CHAR(64) NULL,
    ADD COLUMN owner_user_id BIGINT NULL,
    ADD CONSTRAINT fk_saved_numbers_owner FOREIGN KEY (owner_user_id)
        REFERENCES community_users (id) ON DELETE RESTRICT,
    ADD UNIQUE KEY uk_saved_owner_numbers (owner_user_id, numbers);
