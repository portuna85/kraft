package com.kraft.statistics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 패턴·동반번호 summary가 완전하다고 판단하는 고정 도메인의 단일 소스.
 * {@link StatisticsSummaryRebuilder}(생산자)와 {@link WinningStatisticsCacheService}
 * (완전성 판정자)가 서로 다른 도메인 정의를 쓰면 리빌더가 아무리 다시 계산해도 읽기
 * 측 완전성 검사를 통과하지 못해 매 읽기마다 재계산이 재발동하는 루프가 생긴다.
 */
final class StatisticsSummaryDomain {

    private StatisticsSummaryDomain() {
    }

    // 홀수 개수·고번호 개수 버킷은 0~6개(6개 번호 중 몇 개가 조건을 만족하는지) 총 7가지다.
    static final Set<String> ODD_COUNT_KEYS = Set.of("0", "1", "2", "3", "4", "5", "6");
    static final Set<String> HIGH_COUNT_KEYS = Set.of("0", "1", "2", "3", "4", "5", "6");

    // 45개 번호 전체 쌍 조합 수(45C2=990).
    static final int COMPANION_PAIR_COUNT = 990;

    /** {@code a=1..44, b=a+1..45}의 결정적 순서로 전체 990쌍을 나열한다. */
    static List<int[]> allCompanionPairs() {
        List<int[]> pairs = new ArrayList<>(COMPANION_PAIR_COUNT);
        for (int a = 1; a <= 44; a++) {
            for (int b = a + 1; b <= 45; b++) {
                pairs.add(new int[] {a, b});
            }
        }
        if (pairs.size() != COMPANION_PAIR_COUNT) {
            throw new IllegalStateException("companion pair enumeration produced " + pairs.size()
                    + " pairs, expected " + COMPANION_PAIR_COUNT);
        }
        return pairs;
    }

    /** 주어진 키 전체를 0으로 채운 맵을 만든다 — 관측 여부와 무관하게 완전 도메인을 보장한다. */
    static Map<String, Integer> zeroFilled(Set<String> keys) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (String key : keys) {
            map.put(key, 0);
        }
        return map;
    }
}
