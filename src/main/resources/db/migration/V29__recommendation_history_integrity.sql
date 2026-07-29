-- 역대 1등 조합 배제의 DB 방어선. 45번 비트까지라 BIGINT UNSIGNED 범위 안에 든다.
ALTER TABLE winning_numbers
    ADD COLUMN combination_mask BIGINT UNSIGNED NULL;

UPDATE winning_numbers
SET combination_mask =
      ((CAST(1 AS UNSIGNED) << (n1 - 1)) | (CAST(1 AS UNSIGNED) << (n2 - 1))
    |  (CAST(1 AS UNSIGNED) << (n3 - 1)) | (CAST(1 AS UNSIGNED) << (n4 - 1))
    |  (CAST(1 AS UNSIGNED) << (n5 - 1)) | (CAST(1 AS UNSIGNED) << (n6 - 1)));

ALTER TABLE winning_numbers
    MODIFY COLUMN combination_mask BIGINT UNSIGNED NOT NULL,
    ADD KEY idx_winning_numbers_combination_mask_round (combination_mask, round_no);

ALTER TABLE recommendation_sets
    ADD COLUMN exclusion_policy_version VARCHAR(40) NOT NULL DEFAULT 'legacy-unverified';

ALTER TABLE recommendation_items
    ADD COLUMN combination_mask BIGINT UNSIGNED NULL;

-- recommendation_items.numbers는 LottoNumberCodec의 오름차순 comma 구분 저장 형식이다.
UPDATE recommendation_items
SET combination_mask =
      ((CAST(1 AS UNSIGNED) << (CAST(SUBSTRING_INDEX(numbers, ',', 1) AS UNSIGNED) - 1))
    |  (CAST(1 AS UNSIGNED) << (CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(numbers, ',', 2), ',', -1) AS UNSIGNED) - 1))
    |  (CAST(1 AS UNSIGNED) << (CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(numbers, ',', 3), ',', -1) AS UNSIGNED) - 1))
    |  (CAST(1 AS UNSIGNED) << (CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(numbers, ',', 4), ',', -1) AS UNSIGNED) - 1))
    |  (CAST(1 AS UNSIGNED) << (CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(numbers, ',', 5), ',', -1) AS UNSIGNED) - 1))
    |  (CAST(1 AS UNSIGNED) << (CAST(SUBSTRING_INDEX(numbers, ',', -1) AS UNSIGNED) - 1)));

ALTER TABLE recommendation_items
    MODIFY COLUMN combination_mask BIGINT UNSIGNED NOT NULL,
    ADD KEY idx_recommendation_items_combination_mask (combination_mask);

CREATE TABLE recommendation_history_state (
    id INT NOT NULL,
    version BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT chk_recommendation_history_state_singleton CHECK (id = 1)
);

INSERT INTO recommendation_history_state (id, version) VALUES (1, 1);

CREATE TRIGGER trg_winning_numbers_history_version_insert
AFTER INSERT ON winning_numbers FOR EACH ROW
    UPDATE recommendation_history_state SET version = version + 1 WHERE id = 1;

CREATE TRIGGER trg_winning_numbers_history_version_update
AFTER UPDATE ON winning_numbers FOR EACH ROW
    UPDATE recommendation_history_state SET version = version + 1 WHERE id = 1;

CREATE TRIGGER trg_winning_numbers_history_version_delete
AFTER DELETE ON winning_numbers FOR EACH ROW
    UPDATE recommendation_history_state SET version = version + 1 WHERE id = 1;

DELIMITER //
CREATE TRIGGER trg_recommendation_items_reject_historical_insert
BEFORE INSERT ON recommendation_items FOR EACH ROW
BEGIN
    IF EXISTS (
        SELECT 1
        FROM winning_numbers w
        INNER JOIN recommendation_sets s ON s.id = NEW.set_id
        WHERE w.round_no <= s.history_through_round
          AND w.combination_mask = NEW.combination_mask
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'historical first-prize combination cannot be persisted';
    END IF;
END//

CREATE TRIGGER trg_recommendation_items_reject_historical_update
BEFORE UPDATE ON recommendation_items FOR EACH ROW
BEGIN
    IF EXISTS (
        SELECT 1
        FROM winning_numbers w
        INNER JOIN recommendation_sets s ON s.id = NEW.set_id
        WHERE w.round_no <= s.history_through_round
          AND w.combination_mask = NEW.combination_mask
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'historical first-prize combination cannot be persisted';
    END IF;
END//
DELIMITER ;
