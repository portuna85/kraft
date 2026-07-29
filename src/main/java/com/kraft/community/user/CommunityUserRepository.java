package com.kraft.community.user;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommunityUserRepository extends JpaRepository<CommunityUser, Long> {

    Optional<CommunityUser> findByProviderAndProviderId(String provider, String providerId);

    // KB-14: 계정 저장 경로 직렬화용 — community_users는 계정당 정확히 한 행이 이미
    // 존재하므로, 익명 저장 경로처럼 별도 잠금 테이블을 두지 않고 이 행 자체를 레코드
    // 락으로 잠근다(saved 패키지의 SavedNumbersService에서 사용).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from CommunityUser u where u.id = :id")
    Optional<CommunityUser> lockById(@Param("id") Long id);
}
