CREATE TABLE recommendation_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    set_id BIGINT NOT NULL,
    position INT NOT NULL,
    numbers VARCHAR(32) NOT NULL,
    score INT NULL,
    explanation_codes VARCHAR(255) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_recommendation_items_set_position (set_id, position),
    CONSTRAINT fk_recommendation_items_set FOREIGN KEY (set_id) REFERENCES recommendation_sets (id)
);
