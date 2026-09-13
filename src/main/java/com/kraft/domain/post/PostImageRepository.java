package com.kraft.domain.post;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PostImageRepository extends JpaRepository<PostImage, Long> {

    Optional<PostImage> findByFileName(String fileName);

    List<PostImage> findAllByPostId(Long postId);

    List<PostImage> findAllByStatus(PostImageStatus status);

    List<PostImage> findAllByStatusAndCreatedAtBefore(PostImageStatus status, LocalDateTime threshold);
}
