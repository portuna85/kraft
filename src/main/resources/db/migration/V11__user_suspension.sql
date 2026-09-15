-- 계정 정지(제재).
--
-- 신고 처리로 글을 지워도 같은 사람이 곧바로 다시 쓸 수 있었다. 관리자에게 남은 수단이
-- "지우기"뿐이면 반복하는 사람 앞에서는 아무 소용이 없다.
--
-- 기간을 시각으로 저장하고 매번 현재 시각과 비교한다. 해제 배치가 필요 없고, 배치가 멈춰서
-- 정지가 안 풀리는 일도 없다.
ALTER TABLE users
    ADD COLUMN suspended_until DATETIME(6) NULL;

-- 정지 사유. 정지된 사람에게 그대로 보여주고, 관리자가 나중에 판단을 되짚을 근거가 된다.
ALTER TABLE users
    ADD COLUMN suspension_reason VARCHAR(200) NULL;

-- 지금 정지 중인 계정을 훑는 운영 질의가 쓴다.
CREATE INDEX IX_USERS_SUSPENDED_UNTIL ON users (suspended_until);
