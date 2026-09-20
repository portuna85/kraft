-- 번호 추천 화면의 최신 회차 표시에 쓸 부가 정보. HIST-01 제외 판정(WinningDraw.mask())은
-- 여전히 본번호 6개(n1..n6)만 쓴다 — 이 컬럼들은 그 판정에 관여하지 않는 순수 표시용이며,
-- V20이 "저장하지 않는다"고 밝힌 범위(보너스·등수·당첨금)를 표시 목적으로만 되돌린 것이다.
--
-- 이미 반영된 과거 회차는 이 마이그레이션만으로는 채워지지 않는다(NULL로 남는다). 화면은
-- 최신 회차 하나만 보여주므로, 배포 후 그 회차만 재반영하면 된다(운영 절차, 별도 코드 없음).
ALTER TABLE recommendation_winning_draws
    ADD COLUMN bonus_no INT NULL,
    ADD COLUMN draw_date DATE NULL,
    ADD COLUMN first_prize_winner_count INT NULL,
    ADD COLUMN first_prize_amount BIGINT NULL,
    ADD CONSTRAINT CK_RWD_BONUS_RANGE CHECK (bonus_no BETWEEN 1 AND 45);
