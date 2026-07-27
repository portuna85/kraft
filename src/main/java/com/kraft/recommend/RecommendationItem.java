package com.kraft.recommend;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "recommendation_items")
public class RecommendationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "set_id", nullable = false)
    private Long setId;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "numbers", nullable = false, length = 32)
    private String numbers;

    @Column(name = "score")
    private Integer score;

    @Column(name = "explanation_codes", length = 255)
    private String explanationCodes;

    protected RecommendationItem() {
    }

    public RecommendationItem(Long setId, int position, String numbers, Integer score, String explanationCodes) {
        this.setId = setId;
        this.position = position;
        this.numbers = numbers;
        this.score = score;
        this.explanationCodes = explanationCodes;
    }

    public Long getId() {
        return id;
    }

    public Long getSetId() {
        return setId;
    }

    public int getPosition() {
        return position;
    }

    public String getNumbers() {
        return numbers;
    }

    public Integer getScore() {
        return score;
    }

    public String getExplanationCodes() {
        return explanationCodes;
    }
}
