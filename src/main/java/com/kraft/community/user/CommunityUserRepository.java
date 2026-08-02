package com.kraft.community.user;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommunityUserRepository extends JpaRepository<CommunityUser, Long> {

    Optional<CommunityUser> findByProviderAndProviderId(String provider, String providerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from CommunityUser u where u.id = :id")
    Optional<CommunityUser> lockById(@Param("id") Long id);
}
