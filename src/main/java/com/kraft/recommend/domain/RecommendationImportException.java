package com.kraft.recommend.domain;

import lombok.Getter;

/**
 * 이력 반영({@code RecommendationHistoryImporter}) 검증 실패. 부분 반영을 막기 위해 어떤 회차·어떤
 * 규칙을 위반했는지 하나의 {@code reason} 코드와 사람이 읽을 메시지로 남긴다. REST로 노출되지
 * 않는 내부 도구 전용 예외라 {@code ApiExceptionHandler}에 별도 매핑을 추가하지 않는다.
 */
@Getter
public class RecommendationImportException extends RuntimeException {

    private final String reason;

    public RecommendationImportException(String reason, String message) {
        super(message);
        this.reason = reason;
    }
}
