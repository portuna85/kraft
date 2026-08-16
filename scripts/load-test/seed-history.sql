-- k6 부하 테스트(recommend-and-community.js)용 최소 당첨 이력 시드.
--
-- recommend 시나리오는 historyThroughRound > 0 및 배제 정책 메타데이터를 검증한다 —
-- winning_numbers가 비어 있으면 이 검증 자체가 무의미해진다(성능이 아니라 빈 이력을
-- 재는 꼴). 100회차를 결정론적 공식으로 생성해 매 실행마다 같은 데이터를 보장한다.
--
-- 번호는 무작위 대신 공식으로 만든다 — SQL에서 "1~45 중복 없이 6개, 오름차순"을
-- 진짜 무작위로 뽑으려면 집합 로직이 필요해 번거롭다. start를 1~35 사이로 고정하고
-- 2씩 띄운 6개(n1..n6 = start, start+2, ..., start+10)는 항상 서로 다르고
-- 오름차순이며 45를 넘지 않는다(start<=35 → n6<=45). 보너스는 n1과 n2 사이(start+1)라
-- 나머지 여섯과 짝이 겹치지 않는다.
DELIMITER //
CREATE PROCEDURE kraft_seed_winning_numbers(IN round_count INT)
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE start_number INT;
    WHILE i <= round_count DO
        SET start_number = 1 + ((i - 1) * 2) % 35;
        -- combination_mask는 트리거가 아니라 애플리케이션(LottoBitmask)이 쓰는 값이다 —
        -- 여기서는 V29 마이그레이션의 백필 UPDATE와 같은 공식으로 직접 계산해 넣는다.
        INSERT INTO winning_numbers
            (round_no, draw_date, n1, n2, n3, n4, n5, n6, bonus_number, combination_mask,
             first_prize_amount, second_prize, second_winners, total_sales, first_accum_amount,
             created_at)
        VALUES (
            i,
            DATE_ADD('2020-01-04', INTERVAL (i - 1) WEEK),
            start_number, start_number + 2, start_number + 4,
            start_number + 6, start_number + 8, start_number + 10,
            start_number + 1,
            (CAST(1 AS UNSIGNED) << (start_number - 1))
                | (CAST(1 AS UNSIGNED) << (start_number + 1))
                | (CAST(1 AS UNSIGNED) << (start_number + 3))
                | (CAST(1 AS UNSIGNED) << (start_number + 5))
                | (CAST(1 AS UNSIGNED) << (start_number + 7))
                | (CAST(1 AS UNSIGNED) << (start_number + 9)),
            2000000000 + i * 1000000, 60000000, 10, 90000000000, 2000000000,
            NOW(6)
        );
        SET i = i + 1;
    END WHILE;
END//
DELIMITER ;

CALL kraft_seed_winning_numbers(100);
DROP PROCEDURE kraft_seed_winning_numbers;
