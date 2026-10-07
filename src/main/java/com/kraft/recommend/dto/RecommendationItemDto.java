package com.kraft.recommend.dto;

import java.util.List;

public record RecommendationItemDto(
        int position,
        List<Integer> numbers
) {
}
