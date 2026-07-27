package com.kraft.community.post;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunityPostRepository extends JpaRepository<CommunityPost, Long> {

    List<CommunityPost> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // Phase 2 홈 API의 "이번 주 인기" 절충 구현 — 실제 랭킹 점수는 Phase 3에서 도입된다.
    // 지금은 "최근 7일 이내 최신순"으로만 채운다(문서 13.1절, weeklyPopularPosts 계약 참고).
    List<CommunityPost> findByCreatedAtAfterOrderByCreatedAtDesc(OffsetDateTime since, Pageable pageable);
}
