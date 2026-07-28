package com.kraft.saved;

/** 저장 번호 계정 귀속 결과 — 옮긴 개수와 중복이라 삭제한 개수. */
public record SavedNumberClaimResult(int mergedCount, int duplicateCount) {
}
