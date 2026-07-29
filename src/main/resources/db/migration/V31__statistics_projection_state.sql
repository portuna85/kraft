-- KB-16: 통계 요약(frequency/pattern/companion) rebuild가 "원자적으로" 남기는 프로젝션
-- 상태. 단일 행(id=1)만 쓴다 — rebuild 시점마다 갱신되며, 서빙 쪽(WinningStatisticsCacheService)이
-- source_row_count를 분모(totalRounds)로 사용해 분자(summary 내용)와 같은 스냅숏을 보장한다.
CREATE TABLE statistics_projection_state (
    id BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    last_processed_round INT NOT NULL DEFAULT 0,
    source_row_count BIGINT NOT NULL DEFAULT 0,
    succeeded_at DATETIME(6) NULL,
    PRIMARY KEY (id)
);

-- 기존 summary는 이미 winning_numbers 전체로 만들어져 있으므로, 최초 행은 현재 원본
-- 스냅숏으로 백필한다 — 다음 rebuild 전까지도 분자·분모가 어긋나지 않는다.
INSERT INTO statistics_projection_state (id, version, last_processed_round, source_row_count, succeeded_at)
SELECT 1, 0, COALESCE(MAX(round_no), 0), COUNT(*), NOW(6)
FROM winning_numbers;
