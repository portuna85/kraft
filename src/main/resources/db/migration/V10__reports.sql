-- 신고.
--
-- 공개 게시판에는 스팸과 욕설이 들어온다. 지금까지는 그것을 알릴 방법도, 관리자가 모아서 볼
-- 곳도 없어 누군가 우연히 발견하기를 기다리는 수밖에 없었다.
--
-- 대상을 FK 두 개(post_id/comment_id)로 나누지 않고 target_type + target_id로 두는 이유는
-- 신고 대상이 앞으로 늘 수 있기 때문이다(회원·이미지 등). 대신 FK가 없으므로 대상이 사라진
-- 신고가 남을 수 있는데, 그것은 정상이다 — 처리(삭제)하면 대상은 없어지고 기록은 남아야 한다.
--
-- ENUM으로 두는 이유는 V1의 users.role 주석 참고. 값 목록은 Hibernate가 만드는 것과 같은
-- 알파벳 순이다.
CREATE TABLE reports (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    reporter_id BIGINT NOT NULL,
    target_type ENUM('COMMENT','POST') NOT NULL,
    target_id BIGINT NOT NULL,
    reason ENUM('ABUSE','OTHER','SEXUAL','SPAM') NOT NULL,
    detail VARCHAR(500),
    status ENUM('PENDING','REJECTED','RESOLVED') NOT NULL,
    handled_by_id BIGINT,
    handled_at DATETIME(6),
    PRIMARY KEY (id),
    -- 같은 사람이 같은 대상을 여러 번 신고해도 목록만 부풀 뿐 의미가 없다.
    CONSTRAINT UK_REPORT_REPORTER_TARGET UNIQUE (reporter_id, target_type, target_id),
    CONSTRAINT FK_REPORTS_REPORTER FOREIGN KEY (reporter_id) REFERENCES users (id),
    CONSTRAINT FK_REPORTS_HANDLED_BY FOREIGN KEY (handled_by_id) REFERENCES users (id)
) ENGINE=InnoDB;

-- 관리자 화면은 "아직 처리하지 않은 것"을 오래된 순서로 본다.
CREATE INDEX IX_REPORTS_STATUS_ID ON reports (status, id);

-- 한 대상에 쌓인 신고를 함께 처리할 때 훑는다.
CREATE INDEX IX_REPORTS_TARGET ON reports (target_type, target_id);
