-- 동행복권 최신 회차 자동·수동 수집의 시도 기록. 지금까지 마지막 성공·실패 시각과 연속 실패 횟수는
-- 메모리에만 있어 재시작하면 사라졌고, 관리자 화면(/admin/recommendations)이 "기록 없음"으로 보였다.
-- 한 회차를 한 번 시도할 때마다 한 줄을 남긴다. 주 4회 예약 + 수동 실행뿐이라 행이 거의 늘지 않아
-- 별도 정리 작업을 두지 않는다.
CREATE TABLE recommendation_fetch_attempts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    attempted_at DATETIME(6) NOT NULL,
    trigger_type VARCHAR(20) NOT NULL,
    outcome VARCHAR(20) NOT NULL,
    round_no INT,
    detail VARCHAR(500),
    PRIMARY KEY (id)
) ENGINE=InnoDB;

CREATE INDEX IX_RECOMMENDATION_FETCH_ATTEMPTS_ATTEMPTED_AT ON recommendation_fetch_attempts (attempted_at);
