package com.kraft.winningnumber;

// L-1: WinningNumberCollectionService·WinningNumberBackfillService가 각자 바이트
// 단위로 동일한 sleepQuietly를 갖고 있었다 — 회차 수집 재시도/catch-up 지연에만
// 쓰는 좁은 헬퍼라 패키지 전용으로 둔다(CommunityReactionService의 sleepBriefly는
// 시도 횟수 비례 무작위 지터로 의미가 달라 통합 대상이 아니다).
final class SleepUtils {

    private SleepUtils() {
    }

    static void sleepQuietly(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
