package com.kraft.winningnumber;

import java.time.LocalDate;

public record FirstPrizeHistoryDto(
        int round,
        LocalDate drawDate,
        long firstPrizeAmount
) {
    static FirstPrizeHistoryDto from(WinningNumber winningNumber) {
        return new FirstPrizeHistoryDto(
                winningNumber.getRound(),
                winningNumber.getDrawDate(),
                winningNumber.getFirstPrizeAmount()
        );
    }
}
