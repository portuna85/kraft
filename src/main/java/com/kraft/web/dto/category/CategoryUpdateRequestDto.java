package com.kraft.web.dto.category;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 카테고리 수정 요청 DTO (관리자 전용)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryUpdateRequestDto {

    @NotBlank(message = "카테고리명은 필수입니다")
    private String name;

    private String description;

    @Min(value = 0, message = "표시 순서는 0 이상이어야 합니다")
    private Integer displayOrder;
}

