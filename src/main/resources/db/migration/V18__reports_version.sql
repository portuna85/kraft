-- 두 관리자가 같은 신고를 동시에 resolve/reject하면 나중에 flush되는 쪽이 앞선 처리를
-- 조용히 덮어썼다(개선 보고서 "신고 처리의 동시 확정과 정지 값"). 낙관적 잠금 버전을 두어
-- 그 경우 충돌을 표면화한다 — User·PostImage가 같은 목적으로 쓰는 방식과 같다.
--
-- 기존 행은 0부터 시작한다. 저장된 데이터·현재 버전이 바뀌는 컬럼이 아니므로 별도 백필이
-- 필요 없다.
ALTER TABLE reports ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
