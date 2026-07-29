package com.kraft.recommend;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 당첨 이력의 중앙 버전. winning_numbers 변경 트리거가 이 값을 증가시키며, 각 애플리케이션
 * 인스턴스는 추천 직전에 이 값과 로컬 스냅샷 버전을 대조한다.
 */
@Entity
@Table(name = "recommendation_history_state")
public class RecommendationHistoryState {

    @Id
    private Integer id;

    @Column(name = "version", nullable = false)
    private long version;

    protected RecommendationHistoryState() {
    }

    public long getVersion() {
        return version;
    }
}
