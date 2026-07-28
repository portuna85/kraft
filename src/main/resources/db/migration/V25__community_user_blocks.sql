-- 자기차단 방지는 애플리케이션 레벨에서 검증한다(MariaDB 버전별 CHECK 제약 이식성 문제 회피).
CREATE TABLE community_user_blocks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    blocker_user_id BIGINT NOT NULL,
    blocked_user_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_community_user_blocks_blocker_blocked (blocker_user_id, blocked_user_id),
    CONSTRAINT fk_community_user_blocks_blocker FOREIGN KEY (blocker_user_id)
        REFERENCES community_users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_community_user_blocks_blocked FOREIGN KEY (blocked_user_id)
        REFERENCES community_users (id) ON DELETE RESTRICT
);
