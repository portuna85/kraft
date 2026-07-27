-- Phase 0 초안(검토용, 미적용) — docs/improvement.md §11.5~§11.7, §14.1.

-- §11.5: 좋아요는 사용자와 게시글당 한 번만 허용.
CREATE TABLE community_post_likes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_community_post_likes_post FOREIGN KEY (post_id)
        REFERENCES community_posts (id) ON DELETE CASCADE,
    CONSTRAINT fk_community_post_likes_user FOREIGN KEY (user_id)
        REFERENCES community_users (id) ON DELETE CASCADE,
    UNIQUE KEY uk_community_post_likes_post_user (post_id, user_id)
);

-- §11.5: 북마크는 개인 정보이며 공개 집계에 사용하지 않는다.
CREATE TABLE community_post_bookmarks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_community_post_bookmarks_post FOREIGN KEY (post_id)
        REFERENCES community_posts (id) ON DELETE CASCADE,
    CONSTRAINT fk_community_post_bookmarks_user FOREIGN KEY (user_id)
        REFERENCES community_users (id) ON DELETE CASCADE,
    UNIQUE KEY uk_community_post_bookmarks_post_user (post_id, user_id)
);

-- §11.6: 신고 대상은 게시글·댓글·사용자. target_type + target_id 조합으로 다형 참조한다
-- (테이블이 셋으로 나뉘는 걸 피하기 위함 — FK 대신 애플리케이션 레벨에서 참조 무결성 검증).
-- 동일 사용자의 동일 대상 중복 신고는 하나로 제한한다.
CREATE TABLE community_reports (
    id BIGINT NOT NULL AUTO_INCREMENT,
    reporter_user_id BIGINT NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    target_id BIGINT NOT NULL,
    reason VARCHAR(30) NOT NULL,
    detail VARCHAR(500) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME(6) NOT NULL,
    resolved_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_community_reports_reporter FOREIGN KEY (reporter_user_id)
        REFERENCES community_users (id) ON DELETE CASCADE,
    UNIQUE KEY uk_community_reports_reporter_target (reporter_user_id, target_type, target_id),
    KEY idx_community_reports_status_created (status, created_at)
);

-- §11.6: 차단한 사용자의 글과 댓글은 개인화 단계에서 숨긴다. 차단자 신원은
-- 대상 사용자에게 노출하지 않는다(§15.1) — 이 테이블 자체는 조회 API에서
-- blocker_user_id로만 필터링하고, 피차단자에게 직접 노출하는 API는 만들지 않는다.
CREATE TABLE community_user_blocks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    blocker_user_id BIGINT NOT NULL,
    blocked_user_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_community_user_blocks_blocker FOREIGN KEY (blocker_user_id)
        REFERENCES community_users (id) ON DELETE CASCADE,
    CONSTRAINT fk_community_user_blocks_blocked FOREIGN KEY (blocked_user_id)
        REFERENCES community_users (id) ON DELETE CASCADE,
    UNIQUE KEY uk_community_user_blocks_pair (blocker_user_id, blocked_user_id)
);

-- §11.3: 집계는 별도 metrics 테이블을 단일 진실 공급원으로 사용(공개 게시글 응답의
-- like_count/comment_count/view_count는 여기서 읽는다 — community_posts에 직접 두지 않음).
CREATE TABLE community_post_metrics (
    post_id BIGINT NOT NULL,
    like_count BIGINT NOT NULL DEFAULT 0,
    comment_count BIGINT NOT NULL DEFAULT 0,
    view_count BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (post_id),
    CONSTRAINT fk_community_post_metrics_post FOREIGN KEY (post_id)
        REFERENCES community_posts (id) ON DELETE CASCADE
);

-- §11.3: 주간 인기 점수는 정기 작업으로 여기에 저장한다 — 요청마다 전체 테이블을
-- 계산하지 않는다. weight_version은 가중치 변경 시 올린다(§11.3 "변경 시 버전을 기록").
CREATE TABLE community_post_rankings (
    post_id BIGINT NOT NULL,
    popular_score DECIMAL(14, 6) NOT NULL,
    weight_version VARCHAR(20) NOT NULL,
    computed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (post_id),
    CONSTRAINT fk_community_post_rankings_post FOREIGN KEY (post_id)
        REFERENCES community_posts (id) ON DELETE CASCADE,
    KEY idx_community_post_rankings_score (popular_score DESC)
);

-- §11.6: 운영자 조치는 사유, 수행자, 시각, 이전·이후 상태를 감사 로그로 남긴다.
CREATE TABLE community_moderation_audit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    target_type VARCHAR(20) NOT NULL,
    target_id BIGINT NOT NULL,
    action VARCHAR(30) NOT NULL,
    actor_admin_user_id BIGINT NOT NULL,
    reason VARCHAR(500) NULL,
    previous_status VARCHAR(20) NULL,
    new_status VARCHAR(20) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_community_moderation_audit_target (target_type, target_id, created_at)
);

-- §10.2: 기기 귀속 이력 — 동일 요청 재시도가 같은 결과를 반환해야 하고, 다른 사용자가
-- 이미 귀속한 토큰은 DEVICE_ALREADY_CLAIMED로 거부해야 한다. 이 테이블이 그 판단 근거다.
CREATE TABLE device_claims (
    id BIGINT NOT NULL AUTO_INCREMENT,
    client_token_hash CHAR(64) NOT NULL,
    claimed_by_user_id BIGINT NOT NULL,
    claimed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_device_claims_user FOREIGN KEY (claimed_by_user_id)
        REFERENCES community_users (id) ON DELETE CASCADE,
    UNIQUE KEY uk_device_claims_token (client_token_hash)
);
