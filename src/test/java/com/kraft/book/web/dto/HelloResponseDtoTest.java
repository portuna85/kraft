package com.kraft.book.web.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

public class HelloResponseDtoTest {

    @Test
    void 롬복_기능_테스트() throws Exception {
        // given
        String name = "test";
        int amount = 1000;

        // when
        HelloResponseDto dto = new HelloResponseDto(name, amount);

        // then
        assertThat(dto.getName()).isEqualTo(name);
        assertThat(dto.getAmount()).isEqualTo(amount);

    }
}
