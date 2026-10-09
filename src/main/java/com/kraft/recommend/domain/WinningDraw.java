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
 * 검증된 당첨 회차의 본번호 6개. 이력 제외 판정({@link #mask()})은 본번호만 쓰며, 같은 조합이 여러 회차에 나올 수 있어
 * 번호 조합에는 UNIQUE를 두지 않는다. 보너스·추첨일·1등 당첨자 수·당첨금은 최신 회차 표시 전용 부가 정보로, 판정에
 * 관여하지 않고 null이어도 추천은 동작한다.
 * <p>
 * {@code round_no}가 수동 할당 ID라 {@link Persistable}을 구현한다 — 기본 {@code isNew()}는 ID가 있으면 기존 행으로
 * 보아 새 회차도 merge 경로를 타 반영이 누락될 수 있다.
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

    /** 같은 회차의 정정을 반영한다. 새 detached 인스턴스를 save(merge)하면 영속성 컨텍스트의 관리 인스턴스와 따로 다뤄져 변경이 유실될 수 있어, 관리되는 인스턴스의 필드를 직접 바꾼다. */
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
     * 화면 표시 전용 부가 정보를 반영한다(null이면 이미 알던 값을 지우지 않으려 아무것도 하지 않는다). 자동 수집은
     * {@code DhLotteryClient}가 범위 밖 보너스를 거르지만 수동/CSV 반영은 거치지 않으므로, 값이 있으면 여기서도 최소한의
     * 정합성(범위·중복·음수)을 확인한다. DB CHECK 제약은 운영 마이그레이션 검토가 필요해 두지 않았다.
     */
    public void applyDetails(DrawDetails details) {
        if (details == null) {
            return;
        }
        Integer bonus = details.bonusNo();
        if (bonus != null) {
            if (bonus < LottoNumbers.MIN || bonus > LottoNumbers.MAX) {
                throw new IllegalArgumentException(
                        "보너스 번호가 범위를 벗어났습니다: round=" + roundNo + ", bonusNo=" + bonus);
            }
            if (numbers().contains(bonus)) {
                throw new IllegalArgumentException(
                        "보너스 번호가 본번호와 중복됩니다: round=" + roundNo + ", bonusNo=" + bonus);
            }
        }
        if (details.firstPrizeWinnerCount() != null && details.firstPrizeWinnerCount() < 0) {
            throw new IllegalArgumentException(
                    "1등 당첨자 수는 음수일 수 없습니다: round=" + roundNo + ", count=" + details.firstPrizeWinnerCount());
        }
        if (details.firstPrizeAmount() != null && details.firstPrizeAmount() < 0) {
            throw new IllegalArgumentException(
                    "1등 당첨금은 음수일 수 없습니다: round=" + roundNo + ", amount=" + details.firstPrizeAmount());
        }

        this.bonusNo = bonus;
        this.drawDate = details.drawDate();
        this.firstPrizeWinnerCount = details.firstPrizeWinnerCount();
        this.firstPrizeAmount = details.firstPrizeAmount();
    }
}
