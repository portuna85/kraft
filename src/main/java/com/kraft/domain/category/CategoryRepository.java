package com.kraft.domain.category;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    /**
     * 카테고리명으로 조회
     */
    Optional<Category> findByName(String name);

    /**
     * 카테고리명 존재 여부 확인
     */
    boolean existsByName(String name);

    /**
     * 모든 카테고리 조회 (정렬 순서대로)
     */
    @Query("SELECT c FROM Category c ORDER BY c.displayOrder ASC, c.id ASC")
    List<Category> findAllOrderByDisplayOrder();
}

