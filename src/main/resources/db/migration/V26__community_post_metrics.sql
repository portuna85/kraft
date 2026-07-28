-- 좋아요·댓글·조회 집계의 단일 진실 공급원(문서 11.3/14.2) — 매 조회마다 COUNT 하지 않고
-- 원자적 증감(UPDATE ... SET x = x + 1)으로만 갱신한다.
CREATE TABLE community_post_metrics (
    post_id BIGINT NOT NULL,
    like_count INT NOT NULL DEFAULT 0,
    comment_count INT NOT NULL DEFAULT 0,
    view_count BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (post_id),
    CONSTRAINT fk_community_post_metrics_post FOREIGN KEY (post_id)
        REFERENCES community_posts (id) ON DELETE CASCADE
);

INSERT INTO community_post_metrics (post_id, like_count, comment_count, view_count, updated_at)
SELECT p.id, 0, COALESCE(c.cnt, 0), 0, UTC_TIMESTAMP(6)
FROM community_posts p
LEFT JOIN (
    SELECT post_id, COUNT(*) AS cnt
    FROM community_comments
    WHERE deleted = 0
    GROUP BY post_id
) c ON c.post_id = p.id;
