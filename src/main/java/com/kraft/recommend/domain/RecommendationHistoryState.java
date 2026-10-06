package com.kraft.recommend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 이력 버전·검증 기준 회차를 담는 단일 행(id=1, V20__recommendation_history.sql). version은
 * {@code recommendation_winning_draws}의 INSERT/UPDATE/DELETE 트리거가 증가시킨다(HIST-04/05
 * — 같은 회차 정정·삭제도 버전 변경으로 감지). 메타데이터(이 엔티티 자체 필드) 변경도 버전
 * 증가 대상이므로 갱신 절차는 항상 트리거를 거치는 DML로 수행해야 한다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "recommendation_history_state")
public class RecommendationHistoryState {

    @Id
    private Integer id;

    private Long version;

    @Column(name = "verified_through_round")
    private Integer verifiedThroughRound;

    @Column(name = "source_reference")
    private String sourceReference;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Builder
    public RecommendationHistoryState(Integer id, Long version, Integer verifiedThroughRound,
                                       String sourceReference, LocalDateTime verifiedAt) {
        this.id = id;
        this.version = version;
        this.verifiedThroughRound = verifiedThroughRound;
        this.sourceReference = sourceReference;
        this.verifiedAt = verifiedAt;
    }

}
