package com.kraft.recommend.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecommendationFetchAttemptRepository extends JpaRepository<RecommendationFetchAttempt, Long> {

    /** 최근 시도부터 {@code pageable}만큼. 화면의 이력 목록과 재시작 뒤 상태 복원에 쓴다. */
    List<RecommendationFetchAttempt> findAllByOrderByIdDesc(Pageable pageable);
}
