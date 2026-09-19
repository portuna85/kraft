package com.kraft.recommend.domain;

/**
 * 이력이 비어 있음·누락·미검증·DB 오류·버전 변경으로 추천을 중단할 때 던진다(503,
 * {@code RECOMMENDATION_HISTORY_NOT_READY}, HIST-03/HIST-04). 게시판 전체가 아니라 추천
 * API만 이 오류를 반환한다 — 이력이 없어도 애플리케이션 기동에는 영향을 주지 않는다.
 */
public class RecommendationHistoryNotReadyException extends RuntimeException {

    public static final String CODE = "RECOMMENDATION_HISTORY_NOT_READY";

    public RecommendationHistoryNotReadyException(String message) {
        super(message);
    }
}
