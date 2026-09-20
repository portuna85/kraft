package com.kraft.recommend.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WinningDrawRepository extends JpaRepository<WinningDraw, Integer> {

    Optional<WinningDraw> findTopByOrderByRoundNoDesc();
}
