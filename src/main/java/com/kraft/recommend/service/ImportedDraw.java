package com.kraft.recommend.service;

import com.kraft.recommend.domain.DrawDetails;

import java.util.List;

/**
 * {@link RecommendationHistoryImporter}에 넘기는 검증 전 원자료 한 회차. 번호는 정렬되어
 * 있지 않아도 된다 — Importer가 {@code LottoNumbers.of(...)}로 검증·정렬한다.
 * <p>
 * {@code details}는 화면 표시 전용 부가 정보로 없어도 된다({@code null} 허용) — 2-인자
 * 생성자는 그 경우를 위한 것이다.
 */
public record ImportedDraw(int roundNo, List<Integer> numbers, DrawDetails details) {

    public ImportedDraw(int roundNo, List<Integer> numbers) {
        this(roundNo, numbers, null);
    }
}
