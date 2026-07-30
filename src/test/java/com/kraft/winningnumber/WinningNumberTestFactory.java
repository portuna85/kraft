package com.kraft.winningnumber;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public final class WinningNumberTestFactory {

    private WinningNumberTestFactory() {
    }

    public static WinningNumber create(
            Integer round,
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
            OffsetDateTime createdAt
    ) {
        return WinningNumber.builder()
                .round(round)
                .drawDate(drawDate)
                .drawNumbers(new WinningDrawNumbers(List.of(n1, n2, n3, n4, n5, n6), bonusNumber))
                .firstPrizeAmount(firstPrizeAmount)
                .secondPrize(secondPrize)
                .secondWinners(secondWinners)
                .totalSales(totalSales)
                .firstAccumAmount(firstAccumAmount)
                .createdAt(createdAt)
                .build();
    }
}
