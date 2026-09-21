package com.kraft.recommend.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WinningDrawRepository extends JpaRepository<WinningDraw, Integer> {

    Optional<WinningDraw> findTopByOrderByRoundNoDesc();

    /**
     * 검증 구간(1..verifiedThroughRound)에 이미 있는 회차 번호만 일괄 조회한다(B11).
     * {@code RecommendationHistoryImporter.validate}가 예전에는 회차마다 {@code existsById}를
     * 불러 왕복 수가 회차 수에 비례했다 — 큰 백필일수록(예: 1,000회차 이상) 쿼리 수가
     * 그대로 늘었다. 이 쿼리 한 번으로 대체해 왕복 수를 호출당 1회로 줄인다.
     */
    @Query("SELECT w.roundNo FROM WinningDraw w WHERE w.roundNo BETWEEN :start AND :end")
    List<Integer> findRoundNosBetween(@Param("start") int start, @Param("end") int end);
}
