package com.kraft.post.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * 관리자가 글을 고정할 때 보내는 기한. 시각은 서버와 같은 KST 기준의 오프셋 없는 값이다(브라우저의
 * {@code datetime-local} 입력이 그대로 만드는 형태). 상한(365일)과 동시에 고정할 수 있는 개수는 서비스가
 * 검사한다({@code PostModerationService.pin}).
 */
public record PostPinRequestDto(@NotNull @Future LocalDateTime pinnedUntil) {
}
