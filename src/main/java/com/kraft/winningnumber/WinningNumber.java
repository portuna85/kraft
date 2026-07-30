package com.kraft.winningnumber;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Entity
@Table(name = "winning_numbers")
public class WinningNumber {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "round_no", nullable = false, unique = true)
    private Integer round;

    @Column(name = "draw_date", nullable = false)
    private LocalDate drawDate;

    @Column(name = "n1", nullable = false)
    private Integer n1;

    @Column(name = "n2", nullable = false)
    private Integer n2;

    @Column(name = "n3", nullable = false)
    private Integer n3;

    @Column(name = "n4", nullable = false)
    private Integer n4;

    @Column(name = "n5", nullable = false)
    private Integer n5;

    @Column(name = "n6", nullable = false)
    private Integer n6;

    @Column(name = "combination_mask", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long combinationMask;

    @Column(name = "bonus_number", nullable = false)
    private Integer bonusNumber;

    @Column(name = "first_prize_amount", nullable = false)
    private Long firstPrizeAmount;

    @Column(name = "second_prize", nullable = false)
    private Long secondPrize;

    @Column(name = "second_winners", nullable = false)
    private Integer secondWinners;

    @Column(name = "total_sales", nullable = false)
    private Long totalSales;

    @Column(name = "first_accum_amount", nullable = false)
    private Long firstAccumAmount;

    @jakarta.persistence.Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected WinningNumber() {
    }

    private WinningNumber(Builder builder) {
        this.round = builder.round;
        apply(builder);
        this.createdAt = builder.createdAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 영속 필드가 많으므로 위치 인자 대신 이름 있는 메서드와 추첨번호 값 객체로만 생성한다.
     */
    public static final class Builder {
        private Integer round;
        private LocalDate drawDate;
        private WinningDrawNumbers drawNumbers;
        private Long firstPrizeAmount;
        private Long secondPrize;
        private Integer secondWinners;
        private Long totalSales;
        private Long firstAccumAmount;
        private OffsetDateTime createdAt;

        private Builder() {
        }

        public Builder round(Integer round) {
            this.round = round;
            return this;
        }

        public Builder drawDate(LocalDate drawDate) {
            this.drawDate = drawDate;
            return this;
        }

        public Builder drawNumbers(WinningDrawNumbers drawNumbers) {
            this.drawNumbers = drawNumbers;
            return this;
        }

        public Builder firstPrizeAmount(Long firstPrizeAmount) {
            this.firstPrizeAmount = firstPrizeAmount;
            return this;
        }

        public Builder secondPrize(Long secondPrize) {
            this.secondPrize = secondPrize;
            return this;
        }

        public Builder secondWinners(Integer secondWinners) {
            this.secondWinners = secondWinners;
            return this;
        }

        public Builder totalSales(Long totalSales) {
            this.totalSales = totalSales;
            return this;
        }

        public Builder firstAccumAmount(Long firstAccumAmount) {
            this.firstAccumAmount = firstAccumAmount;
            return this;
        }

        public Builder createdAt(OffsetDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public WinningNumber build() {
            return new WinningNumber(this);
        }

        public void applyUpdateTo(WinningNumber target) {
            target.apply(this);
        }
    }

    private void apply(Builder builder) {
        this.drawDate = builder.drawDate;
        List<Integer> numbers = builder.drawNumbers.mainNumbers();
        this.n1 = numbers.get(0);
        this.n2 = numbers.get(1);
        this.n3 = numbers.get(2);
        this.n4 = numbers.get(3);
        this.n5 = numbers.get(4);
        this.n6 = numbers.get(5);
        this.combinationMask = builder.drawNumbers.combinationMask();
        this.bonusNumber = builder.drawNumbers.bonusNumber();
        this.firstPrizeAmount = builder.firstPrizeAmount;
        this.secondPrize = builder.secondPrize;
        this.secondWinners = builder.secondWinners;
        this.totalSales = builder.totalSales;
        this.firstAccumAmount = builder.firstAccumAmount;
    }

    public Long getId() {
        return id;
    }

    public Integer getRound() {
        return round;
    }

    public LocalDate getDrawDate() {
        return drawDate;
    }

    public Integer getN1() {
        return n1;
    }

    public Integer getN2() {
        return n2;
    }

    public Integer getN3() {
        return n3;
    }

    public Integer getN4() {
        return n4;
    }

    public Integer getN5() {
        return n5;
    }

    public Integer getN6() {
        return n6;
    }

    public Long getCombinationMask() {
        return combinationMask;
    }

    public Integer getBonusNumber() {
        return bonusNumber;
    }

    public Long getFirstPrizeAmount() {
        return firstPrizeAmount;
    }

    public Long getSecondPrize() {
        return secondPrize;
    }

    public Integer getSecondWinners() {
        return secondWinners;
    }

    public Long getTotalSales() {
        return totalSales;
    }

    public Long getFirstAccumAmount() {
        return firstAccumAmount;
    }

    public Long getVersion() {
        return version;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
