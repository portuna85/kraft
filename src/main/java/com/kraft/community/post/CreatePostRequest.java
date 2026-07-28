package com.kraft.community.post;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePostRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 20000) String content,
        // 문자열로 받아 유효하지 않은 값이면 COMMUNITY_CATEGORY_INVALID로 명확히 거절한다
        // (enum 역직렬화 실패의 400 Bad Request 원인 불명 응답을 피하기 위함).
        @NotBlank String category,
        // 첨부할 추천 세트(선택). 있으면 X-Device-Token 헤더가 필수이며, 그 해시가 세트의
        // client_token_hash와 일치해야 한다(문서 11.7, 설계 판단 5).
        Long recommendationSetId
) {
}
