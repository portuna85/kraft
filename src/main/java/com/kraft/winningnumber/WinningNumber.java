package com.kraft.winningnumber;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;

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

    public WinningNumber(Integer round,
                         LocalDate drawDate,
                         Integer n1,
                         Integer n2,
                         Integer n3,
                         Integer n4,
                         Integer n5,
                         Integer n6,
                         Integer bonusNumber,
                         Long firstPrizeAmount,
                         Long secondPrize,
                         Integer secondWinners,
                         Long totalSales,
                         Long firstAccumAmount,
                         OffsetDateTime createdAt) {
        this.round = round;
        this.drawDate = drawDate;
        this.n1 = n1;
        this.n2 = n2;
        this.n3 = n3;
        this.n4 = n4;
        this.n5 = n5;
        this.n6 = n6;
        this.combinationMask = bitmaskOf(n1, n2, n3, n4, n5, n6);
        this.bonusNumber = bonusNumber;
        this.firstPrizeAmount = firstPrizeAmount;
        this.secondPrize = secondPrize;
        this.secondWinners = secondWinners;
        this.totalSales = totalSales;
        this.firstAccumAmount = firstAccumAmount;
        this.createdAt = createdAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 생성자·update()의 위치 인자가 많아(12~14개) 순서 실수 위험이 있어, 실제 값이
     * 외부 API 응답에서 동적으로 채워지는 프로덕션 호출부(WinningNumberCommandService)는
     * 이 빌더를 통해 이름으로 값을 지정한다. 테스트 픽스처처럼 리터럴을 직접 나열하는
     * 곳은 기존 생성자를 계속 써도 무방하다.
     */
    public static final class Builder {
        private Integer round;
        private LocalDate drawDate;
        private Integer n1;
        private Integer n2;
        private Integer n3;
        private Integer n4;
        private Integer n5;
        private Integer n6;
        private Integer bonusNumber;
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

        public Builder numbers(Integer n1, Integer n2, Integer n3, Integer n4, Integer n5, Integer n6) {
            this.n1 = n1;
            this.n2 = n2;
            this.n3 = n3;
            this.n4 = n4;
            this.n5 = n5;
            this.n6 = n6;
            return this;
        }

        public Builder bonusNumber(Integer bonusNumber) {
            this.bonusNumber = bonusNumber;
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
            return new WinningNumber(round, drawDate, n1, n2, n3, n4, n5, n6, bonusNumber,
                    firstPrizeAmount, secondPrize, secondWinners, totalSales, firstAccumAmount, createdAt);
        }

        public void applyUpdateTo(WinningNumber target) {
            target.update(drawDate, n1, n2, n3, n4, n5, n6, bonusNumber,
                    firstPrizeAmount, secondPrize, secondWinners, totalSales, firstAccumAmount);
        }
    }

    public void update(LocalDate drawDate,
                       Integer n1,
                       Integer n2,
                       Integer n3,
                       Integer n4,
                       Integer n5,
                       Integer n6,
                       Integer bonusNumber,
                       Long firstPrizeAmount,
                       Long secondPrize,
                       Integer secondWinners,
                       Long totalSales,
                       Long firstAccumAmount) {
        this.drawDate = drawDate;
        this.n1 = n1;
        this.n2 = n2;
        this.n3 = n3;
        this.n4 = n4;
        this.n5 = n5;
        this.n6 = n6;
        this.combinationMask = bitmaskOf(n1, n2, n3, n4, n5, n6);
        this.bonusNumber = bonusNumber;
        this.firstPrizeAmount = firstPrizeAmount;
        this.secondPrize = secondPrize;
        this.secondWinners = secondWinners;
        this.totalSales = totalSales;
        this.firstAccumAmount = firstAccumAmount;
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

    private static long bitmaskOf(int n1, int n2, int n3, int n4, int n5, int n6) {
        return (1L << (n1 - 1)) | (1L << (n2 - 1)) | (1L << (n3 - 1))
                | (1L << (n4 - 1)) | (1L << (n5 - 1)) | (1L << (n6 - 1));
    }
}
