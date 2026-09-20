package com.kraft.recommend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 검증된 당첨 회차의 본번호 6개(V20__recommendation_history.sql). 이력 제외 판정(HIST-01,
 * {@link #mask()})은 본번호 6개만 쓴다 — 동일 조합이 여러 회차에 나올 수 있으므로 번호 조합에는
 * UNIQUE 제약을 두지 않는다.
 * <p>
 * 보너스 번호·추첨일·1등 당첨자 수·1등 1인당 당첨금(V21__recommendation_winning_draw_details.sql)은
 * 번호 추천 화면이 최신 회차를 보여줄 때만 쓰는 순수 표시용 부가 정보다 — HIST-01 판정에는
 * 전혀 관여하지 않으며, 없어도(과거에 반영된 회차처럼 전부 null이어도) 추천 기능은 그대로
 * 동작한다.
 * <p>
 * {@code round_no}가 자동 생성이 아니라 수동 할당 ID라서 {@link Persistable}을 구현한다 —
 * 그렇지 않으면 Spring Data JPA의 기본 {@code isNew()} 판정(ID가 null이 아니면 "기존 행")이
 * 새로 만든 인스턴스를 항상 merge 대상으로 취급해, 실제로는 아직 없는 회차인데도 INSERT
 * 대신 애매한 merge 경로를 타 반영이 누락될 수 있다(MariaDB 실측으로 확인).
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "recommendation_winning_draws")
public class WinningDraw implements Persistable<Integer> {

    @Id
    @Column(name = "round_no")
    private Integer roundNo;

    @Transient
    private boolean isNew = true;

    @Column(nullable = false)
    private Integer n1;
    @Column(nullable = false)
    private Integer n2;
    @Column(nullable = false)
    private Integer n3;
    @Column(nullable = false)
    private Integer n4;
    @Column(nullable = false)
    private Integer n5;
    @Column(nullable = false)
    private Integer n6;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "bonus_no")
    private Integer bonusNo;
    @Column(name = "draw_date")
    private LocalDate drawDate;
    @Column(name = "first_prize_winner_count")
    private Integer firstPrizeWinnerCount;
    @Column(name = "first_prize_amount")
    private Long firstPrizeAmount;

    @Builder
    public WinningDraw(Integer roundNo, List<Integer> numbers, LocalDateTime updatedAt) {
        if (numbers.size() != LottoNumbers.SIZE) {
            throw new IllegalArgumentException("당첨 번호는 6개여야 합니다: round=" + roundNo);
        }
        this.roundNo = roundNo;
        this.n1 = numbers.get(0);
        this.n2 = numbers.get(1);
        this.n3 = numbers.get(2);
        this.n4 = numbers.get(3);
        this.n5 = numbers.get(4);
        this.n6 = numbers.get(5);
        this.updatedAt = updatedAt;
    }

    @Override
    public Integer getId() {
        return roundNo;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    public List<Integer> numbers() {
        return List.of(n1, n2, n3, n4, n5, n6);
    }

    public long mask() {
        return LottoBitmask.maskOf(numbers());
    }

    /**
     * 같은 회차의 정정을 반영한다(HIST-05, {@code RecommendationHistoryImporter}). 관리되는
     * 엔티티 인스턴스 자신의 필드를 직접 바꾼다 — 새 detached 인스턴스를 만들어
     * {@code repository.save()}(merge)로 반영하면, 이미 영속성 컨텍스트에 있는 같은 PK의 관리
     * 인스턴스와 별개로 다뤄져 변경이 감지되지 않고 조용히 유실될 수 있다(로컬 실측으로 확인).
     */
    public void replaceNumbers(List<Integer> numbers, LocalDateTime updatedAt) {
        if (numbers.size() != LottoNumbers.SIZE) {
            throw new IllegalArgumentException("당첨 번호는 6개여야 합니다: round=" + roundNo);
        }
        this.n1 = numbers.get(0);
        this.n2 = numbers.get(1);
        this.n3 = numbers.get(2);
        this.n4 = numbers.get(3);
        this.n5 = numbers.get(4);
        this.n6 = numbers.get(5);
        this.updatedAt = updatedAt;
    }

    /**
     * 화면 표시 전용 부가 정보를 반영한다(HIST-01 판정과 무관). {@code details}가 null이면
     * 아무것도 하지 않는다 — 부가 정보를 못 받아온 반영(details 없는 {@code ImportedDraw})이
     * 이미 알고 있던 부가 정보를 조용히 지우지 않게 하기 위함이다.
     */
    public void applyDetails(DrawDetails details) {
        if (details == null) {
            return;
        }
        this.bonusNo = details.bonusNo();
        this.drawDate = details.drawDate();
        this.firstPrizeWinnerCount = details.firstPrizeWinnerCount();
        this.firstPrizeAmount = details.firstPrizeAmount();
    }
}
