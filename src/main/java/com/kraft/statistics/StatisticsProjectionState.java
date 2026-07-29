package com.kraft.statistics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * KB-16: 통계 요약 rebuild가 원자적으로 남기는 프로젝션 상태 — 단일 행(id=1)만 쓴다.
 * {@code sourceRowCount}는 rebuild가 실제로 스캔한 winning_numbers 행 수로, 서빙 쪽이
 * 이 값을 totalRounds(분모)로 써서 summary 내용(분자)과 같은 스냅숏을 보장한다.
 */
@Entity
@Table(name = "statistics_projection_state")
public class StatisticsProjectionState {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "last_processed_round", nullable = false)
    private int lastProcessedRound;

    @Column(name = "source_row_count", nullable = false)
    private long sourceRowCount;

    @Column(name = "succeeded_at")
    private OffsetDateTime succeededAt;

    protected StatisticsProjectionState() {
    }

    public StatisticsProjectionState(long id) {
        this.id = id;
        this.version = 0;
        this.lastProcessedRound = 0;
        this.sourceRowCount = 0;
    }

    public void recordSuccess(int lastProcessedRound, long sourceRowCount, OffsetDateTime succeededAt) {
        this.version++;
        this.lastProcessedRound = lastProcessedRound;
        this.sourceRowCount = sourceRowCount;
        this.succeededAt = succeededAt;
    }

    public Long getId() {
        return id;
    }

    public long getVersion() {
        return version;
    }

    public int getLastProcessedRound() {
        return lastProcessedRound;
    }

    public long getSourceRowCount() {
        return sourceRowCount;
    }

    public OffsetDateTime getSucceededAt() {
        return succeededAt;
    }
}
