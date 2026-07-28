-- 신고 대상은 게시글/댓글/사용자 세 종류이므로 다형 참조를 target_type+target_id로 표현한다
-- (문서 11.6). 동일 사용자의 동일 대상 중복 신고는 UNIQUE 제약으로 원천 차단한다.
CREATE TABLE community_reports (
    id BIGINT NOT NULL AUTO_INCREMENT,
    reporter_user_id BIGINT NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    target_id BIGINT NOT NULL,
    reason VARCHAR(40) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_community_reports_reporter_target (reporter_user_id, target_type, target_id),
    CONSTRAINT fk_community_reports_reporter FOREIGN KEY (reporter_user_id)
        REFERENCES community_users (id) ON DELETE RESTRICT
);
