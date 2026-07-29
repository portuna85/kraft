package com.kraft.recommend;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;

public record RecommendNumbersRequest(
        @Min(1) @Max(10) Integer count,
        List<Integer> excludedNumbers,
        // 필드명이 "당첨금 최대화"처럼 읽혀 전체 조합을 최적화한다는 오해를 줄 수 있어
        // 의도(공동 당첨 위험 감소)를 드러내는 이름으로 바꿨다.
        Boolean reduceSharedWinnerRisk,
        // 신규 필드. "random"/"balanced"/"reduce_shared_winner_risk" 중 하나(대소문자 무관).
        // 값이 있으면 reduceSharedWinnerRisk보다 우선한다 — 두 필드가 서로
        // 다른 전략을 가리켜도 오류로 거부하지 않고 strategy를 신뢰한다.
        String strategy,
        // 고정 번호. 중복 없이 0~5개, 각 1~45. 결과 조합마다 반드시 포함된다.
        List<Integer> lockedNumbers,
        // 구 필드명 — 같은 JSON 키를 두 프로퍼티에 동시에 매핑할 수 없어(@JsonAlias 사용 시
        // 실제로 어느 키로 들어왔는지 알 방법이 없어짐) 별도 필드로 받는다.
        // 구 필드명이 실제로 아직 쓰이는지 확인한 뒤에만 이 필드를 제거하기로 했다
        // (effectiveReduceSharedWinnerRisk()가 사용량 계측 지점).
        Boolean maximizePrize
) {
    /** reduceSharedWinnerRisk가 없으면 구 필드명(maximizePrize) 값을 대신 쓴다. */
    Boolean effectiveReduceSharedWinnerRisk() {
        return reduceSharedWinnerRisk != null ? reduceSharedWinnerRisk : maximizePrize;
    }
}
