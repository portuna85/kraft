package com.kraft.recommend;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;

public record RecommendNumbersRequest(
        @Min(1) @Max(10) Integer count,
        List<Integer> excludedNumbers,
        // 필드명이 "당첨금 최대화"처럼 읽혀 전체 조합을 최적화한다는 오해를 줄 수 있어
        // 의도(공동 당첨 위험 감소)를 드러내는 이름으로 바꿨다. 기존 클라이언트가 보내는
        // maximizePrize도 계속 받아들인다(@JsonAlias) — 필드 삭제가 아니라 이름만 바뀐
        // 전환이므로 별도 응답 스키마 변화 없이 구버전 요청도 그대로 동작한다.
        @JsonAlias("maximizePrize") Boolean reduceSharedWinnerRisk,
        // 신규 필드. "random"/"balanced"/"reduce_shared_winner_risk" 중 하나(대소문자 무관).
        // 값이 있으면 reduceSharedWinnerRisk보다 우선한다(문서 13.2절) — 두 필드가 서로
        // 다른 전략을 가리켜도 오류로 거부하지 않고 strategy를 신뢰한다.
        String strategy,
        // 고정 번호. 중복 없이 0~5개, 각 1~45. 결과 조합마다 반드시 포함된다.
        List<Integer> lockedNumbers
) {
}
