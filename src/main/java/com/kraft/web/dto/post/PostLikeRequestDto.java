package com.kraft.web.dto.post;

import jakarta.validation.constraints.NotNull;

/**
 * 추천 상태 변경 요청. 토글이 아니라 <b>원하는 최종 상태</b>를 싣는다 — 네트워크 재시도로
 * 같은 요청이 두 번 도달해도 사용자의 의도가 뒤집히지 않게 하려는 것이다(개선 보고서 F10).
 */
public record PostLikeRequestDto(

        @NotNull(message = "추천 여부는 필수입니다.")
        Boolean liked
) {
}
