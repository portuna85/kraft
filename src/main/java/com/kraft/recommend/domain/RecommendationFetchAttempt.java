package com.kraft.recommend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 최신 회차 수집을 한 회차 시도한 기록(V38). 관리자 화면의 "최근 수집 이력"과, 재시작 뒤에도
 * 남아야 하는 마지막 성공·실패·연속 실패 횟수의 근거다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "recommendation_fetch_attempts")
public class RecommendationFetchAttempt {

    /** 시도를 일으킨 쪽. */
    public enum Trigger {
        SCHEDULED, MANUAL
    }

    /** 시도 결과. */
    public enum Outcome {
        /** 새 회차를 받아 반영했다. */
        FETCHED,
        /** 아직 추첨 전이라는 정상 응답을 받았다. */
        NOT_YET_DRAWN,
        /** 신뢰할 수 없는 응답이거나 검증에 실패해 반영하지 못했다. */
        FAILED;

        /** 실패가 아니면 "응답을 신뢰해 처리했다"로 본다(RecommendationFetchStatus.recordSuccess와 같다). */
        public boolean isSuccessLike() {
            return this != FAILED;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attempted_at", nullable = false)
    private LocalDateTime attemptedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 20)
    private Trigger trigger;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Outcome outcome;

    @Column(name = "round_no")
    private Integer roundNo;

    @Column(length = 500)
    private String detail;

    @Builder
    public RecommendationFetchAttempt(LocalDateTime attemptedAt, Trigger trigger, Outcome outcome,
                                      Integer roundNo, String detail) {
        this.attemptedAt = attemptedAt;
        this.trigger = trigger;
        this.outcome = outcome;
        this.roundNo = roundNo;
        // 외부 응답 본문이 길 수 있다 — 컬럼 길이에 맞춰 자른다.
        this.detail = detail != null && detail.length() > 500 ? detail.substring(0, 500) : detail;
    }
}
