-- M-04: client_token_hash(익명)/owner_user_id(계정) 상호 배타 소유권은 지금까지
-- 애플리케이션(SavedNumber.claimTo/RecommendationSet.claimTo)에서만 지켜졌다 — DB에는
-- 강제하는 제약이 없어, 버그나 수동 스크립트로 두 컬럼이 동시에 NULL이거나 동시에
-- 값이 있는 행이 만들어져도 막을 방법이 없었다. CHECK 제약을 추가하면 ALTER TABLE 자체가
-- 기존 데이터를 검증하므로, 이 마이그레이션이 실패 없이 끝나는 것 자체가 "현재 위반 행이
-- 없다"는 감사 결과다.
ALTER TABLE saved_numbers
    ADD CONSTRAINT chk_saved_numbers_owner_xor
        CHECK ((client_token_hash IS NULL) <> (owner_user_id IS NULL));

ALTER TABLE recommendation_sets
    ADD CONSTRAINT chk_recommendation_sets_owner_xor
        CHECK ((client_token_hash IS NULL) <> (owner_user_id IS NULL));

-- recommendation_sets.owner_user_id는 지금까지 FK가 없었다 — saved_numbers(V27)와 달리
-- 고아 owner_user_id(존재하지 않는 계정을 가리킴)를 DB가 막지 못했다.
-- CommunityWithdrawalService.withdraw()가 이미 RecommendationAccountDataDeletionHandler로
-- community_users 삭제 전에 해당 계정의 recommendation_sets를 전부 삭제하므로(V32와 같은
-- "먼저 정리 후 삭제" 순서), ON DELETE RESTRICT를 추가해도 기존 탈퇴 흐름과 충돌하지 않는다.
ALTER TABLE recommendation_sets
    ADD CONSTRAINT fk_recommendation_sets_owner FOREIGN KEY (owner_user_id)
        REFERENCES community_users (id) ON DELETE RESTRICT;
