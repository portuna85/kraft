package com.kraft.saved;

/**
 * 저장 번호 계정 귀속 결과 — 옮긴 개수, 중복이라 삭제한 개수.
 *
 * <p>M-02: {@code skippedForLimitCount}는 계정의 저장 개수 한도(maxPerClient)를 넘어서
 * 병합하지 않고 익명(기기 토큰) 상태 그대로 남겨 둔 개수다 — 지워지지 않으므로 사용자가
 * 나중에 직접 정리한 뒤 다시 저장할 수 있다.
 */
public record SavedNumberClaimResult(int mergedCount, int duplicateCount, int skippedForLimitCount) {
}
