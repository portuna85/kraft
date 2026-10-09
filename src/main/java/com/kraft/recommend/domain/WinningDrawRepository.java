package com.kraft.recommend.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WinningDrawRepository extends JpaRepository<WinningDraw, Integer> {

    Optional<WinningDraw> findTopByOrderByRoundNoDesc();

    /** 검증 구간(1..verifiedThroughRound)에 이미 있는 회차 번호만 일괄 조회한다 — 회차마다 {@code existsById}를 부르지 않아 왕복 수가 호출당 1회다. */
    @Query("SELECT w.roundNo FROM WinningDraw w WHERE w.roundNo BETWEEN :start AND :end")
    List<Integer> findRoundNosBetween(@Param("start") int start, @Param("end") int end);
}
