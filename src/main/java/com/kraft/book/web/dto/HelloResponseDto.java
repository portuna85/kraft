package com.kraft.book.web.dto;

public record HelloResponseDto(String name, int amount) {
    public String getName() {
        return name;
    }

    public int getAmount() {
        return amount;
    }
}
