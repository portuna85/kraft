package com.kraft.domain.post;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PostRepository extends JpaRepository<Post, Long> {

    @Query(value = "SELECT p FROM Post p JOIN FETCH p.user ORDER BY p.id DESC",
            countQuery = "SELECT COUNT(p) FROM Post p")
    Page<Post> findAllDesc(Pageable pageable);
}
