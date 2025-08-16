package com.example.board.file;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
    List<Attachment> findByPostIdOrderByCreatedAtAsc(Long postId);
}
