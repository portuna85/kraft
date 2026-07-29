-- V29 이전 애플리케이션 이미지는 combination_mask 컬럼을 INSERT/UPDATE SQL에 포함하지
-- 않는다. expand 마이그레이션 뒤 이미지 롤백이 일어나도 회차 수집과 추천 저장이 계속
-- 동작하도록 DB가 정규 저장 문자열/번호 컬럼에서 마스크를 계산한다.
DELIMITER //
CREATE TRIGGER trg_winning_numbers_fill_mask_insert
BEFORE INSERT ON winning_numbers FOR EACH ROW
BEGIN
    SET NEW.combination_mask =
          ((CAST(1 AS UNSIGNED) << (NEW.n1 - 1)) | (CAST(1 AS UNSIGNED) << (NEW.n2 - 1))
        |  (CAST(1 AS UNSIGNED) << (NEW.n3 - 1)) | (CAST(1 AS UNSIGNED) << (NEW.n4 - 1))
        |  (CAST(1 AS UNSIGNED) << (NEW.n5 - 1)) | (CAST(1 AS UNSIGNED) << (NEW.n6 - 1)));
END//

CREATE TRIGGER trg_winning_numbers_fill_mask_update
BEFORE UPDATE ON winning_numbers FOR EACH ROW
BEGIN
    SET NEW.combination_mask =
          ((CAST(1 AS UNSIGNED) << (NEW.n1 - 1)) | (CAST(1 AS UNSIGNED) << (NEW.n2 - 1))
        |  (CAST(1 AS UNSIGNED) << (NEW.n3 - 1)) | (CAST(1 AS UNSIGNED) << (NEW.n4 - 1))
        |  (CAST(1 AS UNSIGNED) << (NEW.n5 - 1)) | (CAST(1 AS UNSIGNED) << (NEW.n6 - 1)));
END//

CREATE TRIGGER trg_recommendation_items_fill_mask_insert
BEFORE INSERT ON recommendation_items FOR EACH ROW
PRECEDES trg_recommendation_items_reject_historical_insert
BEGIN
    SET NEW.combination_mask =
          ((CAST(1 AS UNSIGNED) << (CAST(SUBSTRING_INDEX(NEW.numbers, ',', 1) AS UNSIGNED) - 1))
        |  (CAST(1 AS UNSIGNED) << (CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(NEW.numbers, ',', 2), ',', -1) AS UNSIGNED) - 1))
        |  (CAST(1 AS UNSIGNED) << (CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(NEW.numbers, ',', 3), ',', -1) AS UNSIGNED) - 1))
        |  (CAST(1 AS UNSIGNED) << (CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(NEW.numbers, ',', 4), ',', -1) AS UNSIGNED) - 1))
        |  (CAST(1 AS UNSIGNED) << (CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(NEW.numbers, ',', 5), ',', -1) AS UNSIGNED) - 1))
        |  (CAST(1 AS UNSIGNED) << (CAST(SUBSTRING_INDEX(NEW.numbers, ',', -1) AS UNSIGNED) - 1)));
END//

CREATE TRIGGER trg_recommendation_items_fill_mask_update
BEFORE UPDATE ON recommendation_items FOR EACH ROW
PRECEDES trg_recommendation_items_reject_historical_update
BEGIN
    SET NEW.combination_mask =
          ((CAST(1 AS UNSIGNED) << (CAST(SUBSTRING_INDEX(NEW.numbers, ',', 1) AS UNSIGNED) - 1))
        |  (CAST(1 AS UNSIGNED) << (CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(NEW.numbers, ',', 2), ',', -1) AS UNSIGNED) - 1))
        |  (CAST(1 AS UNSIGNED) << (CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(NEW.numbers, ',', 3), ',', -1) AS UNSIGNED) - 1))
        |  (CAST(1 AS UNSIGNED) << (CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(NEW.numbers, ',', 4), ',', -1) AS UNSIGNED) - 1))
        |  (CAST(1 AS UNSIGNED) << (CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(NEW.numbers, ',', 5), ',', -1) AS UNSIGNED) - 1))
        |  (CAST(1 AS UNSIGNED) << (CAST(SUBSTRING_INDEX(NEW.numbers, ',', -1) AS UNSIGNED) - 1)));
END//
DELIMITER ;
