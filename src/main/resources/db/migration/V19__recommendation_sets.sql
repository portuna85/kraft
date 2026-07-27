CREATE TABLE recommendation_sets (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_user_id BIGINT NULL,
    client_token_hash CHAR(64) NULL,
    strategy VARCHAR(40) NOT NULL,
    algorithm_version VARCHAR(40) NOT NULL,
    history_through_round INT NOT NULL,
    locked_numbers VARCHAR(32) NULL,
    excluded_numbers VARCHAR(160) NULL,
    created_at DATETIME(6) NOT NULL,
    claimed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_recommendation_sets_client_created (client_token_hash, created_at DESC)
);
