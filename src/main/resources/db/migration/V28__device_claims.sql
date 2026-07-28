-- 기기 토큰이 어느 계정에 이미 귀속됐는지 판정하는 이력 테이블(문서 14.1). 귀속되는
-- saved_numbers/recommendation_sets 행은 client_token_hash를 null로 비우므로, 데이터
-- 행만으로는 "이미 귀속됨"을 판정할 수 없다 — 이 테이블이 그 판정의 단일 진실 공급원이다.
CREATE TABLE device_claims (
    id BIGINT NOT NULL AUTO_INCREMENT,
    device_token_hash CHAR(64) NOT NULL,
    claimed_by_user_id BIGINT NOT NULL,
    claimed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_claims_token_hash (device_token_hash),
    CONSTRAINT fk_device_claims_user FOREIGN KEY (claimed_by_user_id)
        REFERENCES community_users (id) ON DELETE RESTRICT
);
