package com.kraft.recommend.service;

import java.util.List;

/**
 * {@link RecommendationHistoryImporter}에 넘기는 검증 전 원자료 한 회차. 번호는 정렬되어
 * 있지 않아도 된다 — Importer가 {@code LottoNumbers.of(...)}로 검증·정렬한다.
 */
public record ImportedDraw(int roundNo, List<Integer> numbers) {
}
