-- 번호 추천의 과거 당첨 조합 제외 규칙(HIST-01~08, docs/03-number-recommendation-policy.md)에
-- 필요한 최소 이력 저장소다. 당첨 이력의 INSERT/UPDATE/DELETE마다 recommendation_history_state
-- 의 version을 증가시켜야 한다 — 같은 회차의 "정정"도 최대 회차 비교만으로는 감지할 수 없기
-- 때문이다(01문서 6절). 이 코드베이스는 보통 DB 트리거를 쓰지 않지만(V19 주석 참고, 연쇄
-- 삭제는 항상 서비스 계층에서 처리), 버전 증가는 이 테이블에 쓰는 모든 경로(운영 절차·수동
-- SQL 포함)에서 예외 없이 일어나야 하는 불변식이라 애플리케이션 계층에 맡기지 않고 트리거로
-- 강제한다.
--
-- 보너스 번호·등수·당첨금·사용자 ID·추천 결과 테이블은 만들지 않는다(비저장·본번호 6개
-- 완전 일치 제외만 필요). 동일한 번호 조합이 여러 회차에 나올 수 있으므로 번호 조합에는
-- UNIQUE 제약을 두지 않는다.
--
-- 이 마이그레이션은 데이터를 비운 채로 배포한다. 운영 이력 반영은 별도 절차이며, 이력이
-- 비어 있는 동안 추천 API는 503(RECOMMENDATION_HISTORY_NOT_READY)을 반환한다.

CREATE TABLE recommendation_winning_draws (
    round_no   INT PRIMARY KEY,
    n1         INT NOT NULL,
    n2         INT NOT NULL,
    n3         INT NOT NULL,
    n4         INT NOT NULL,
    n5         INT NOT NULL,
    n6         INT NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT CK_RWD_ROUND_POSITIVE CHECK (round_no > 0),
    CONSTRAINT CK_RWD_N1_RANGE CHECK (n1 BETWEEN 1 AND 45),
    CONSTRAINT CK_RWD_N2_RANGE CHECK (n2 BETWEEN 1 AND 45),
    CONSTRAINT CK_RWD_N3_RANGE CHECK (n3 BETWEEN 1 AND 45),
    CONSTRAINT CK_RWD_N4_RANGE CHECK (n4 BETWEEN 1 AND 45),
    CONSTRAINT CK_RWD_N5_RANGE CHECK (n5 BETWEEN 1 AND 45),
    CONSTRAINT CK_RWD_N6_RANGE CHECK (n6 BETWEEN 1 AND 45),
    CONSTRAINT CK_RWD_ASCENDING CHECK (n1 < n2 AND n2 < n3 AND n3 < n4 AND n4 < n5 AND n5 < n6)
);

CREATE TABLE recommendation_history_state (
    id                      INT PRIMARY KEY,
    version                 BIGINT NOT NULL DEFAULT 0,
    verified_through_round  INT NOT NULL DEFAULT 0,
    source_reference        VARCHAR(255) NULL,
    verified_at             DATETIME NULL
);

INSERT INTO recommendation_history_state (id, version, verified_through_round)
VALUES (1, 0, 0);

CREATE TRIGGER TRG_RWD_VERSION_AFTER_INSERT
    AFTER INSERT ON recommendation_winning_draws
    FOR EACH ROW
    UPDATE recommendation_history_state SET version = version + 1 WHERE id = 1;

CREATE TRIGGER TRG_RWD_VERSION_AFTER_UPDATE
    AFTER UPDATE ON recommendation_winning_draws
    FOR EACH ROW
    UPDATE recommendation_history_state SET version = version + 1 WHERE id = 1;

CREATE TRIGGER TRG_RWD_VERSION_AFTER_DELETE
    AFTER DELETE ON recommendation_winning_draws
    FOR EACH ROW
    UPDATE recommendation_history_state SET version = version + 1 WHERE id = 1;
