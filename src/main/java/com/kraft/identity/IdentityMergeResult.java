package com.kraft.identity;

/** 기기 귀속(claim-device) 결과 — 옮긴 저장 번호·추천 세트 개수. */
public record IdentityMergeResult(int mergedSavedNumberCount, int duplicateSavedNumberCount,
                                   int mergedRecommendationSetCount) {
}
