package com.kraft.recommend.domain;

import lombok.Getter;

/**
 * 요청 검증·실현 가능성 실패(400). {@code code}는 02문서 4절 오류 표의 안정적인 식별자다
 * (예: {@code INVALID_NUMBERS}, {@code TOO_MANY_LOCKED_NUMBERS}, {@code LOCKED_EXCLUDED_CONFLICT},
 * {@code INSUFFICIENT_UNIQUE_COMBINATIONS} 등). {@link IllegalArgumentException}을 상속해
 * 전용 핸들러가 없어도 기존 {@code ApiExceptionHandler.handleIllegalArgument}가 400으로
 * 안전하게 처리하고, 전용 핸들러가 있으면 더 구체적인 타입이 우선해 {@code code} 속성을
 * 추가로 붙인다(PostNotFoundException과 동일한 패턴).
 */
@Getter
public class RecommendationValidationException extends IllegalArgumentException {

    private final String code;

    public RecommendationValidationException(String code, String message) {
        super(message);
        this.code = code;
    }
}
