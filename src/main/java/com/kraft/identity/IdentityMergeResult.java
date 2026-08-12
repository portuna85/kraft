package com.kraft.identity;

/**
 * 기기 귀속(claim-device) 결과 — 옮긴 저장 번호·추천 세트 개수.
 *
 * <p>M-02: {@code skippedSavedNumberForLimitCount}는 계정 저장 한도 때문에 병합하지
 * 않고 익명 상태로 남긴 개수다({@link com.kraft.saved.SavedNumberClaimResult} 참고).
 */
public record IdentityMergeResult(int mergedSavedNumberCount, int duplicateSavedNumberCount,
                                   int skippedSavedNumberForLimitCount,
                                   int mergedRecommendationSetCount) {
}
