package com.kraft.saved;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavedNumberRepository extends JpaRepository<SavedNumber, Long> {

    List<SavedNumber> findByClientTokenHashOrderByCreatedAtDesc(String clientTokenHash);

    long countByClientTokenHash(String clientTokenHash);

    Optional<SavedNumber> findByClientTokenHashAndNumbers(String clientTokenHash, String numbers);

    Optional<SavedNumber> findByIdAndClientTokenHash(Long id, String clientTokenHash);

    List<SavedNumber> findByOwnerUserIdOrderByCreatedAtDesc(Long ownerUserId);

    Optional<SavedNumber> findByOwnerUserIdAndNumbers(Long ownerUserId, String numbers);

    // M-8: claimAll이 익명 행마다 findByOwnerUserIdAndNumbers를 호출하던 N+1을 없애는 배치 조회.
    List<SavedNumber> findByOwnerUserIdAndNumbersIn(Long ownerUserId, Collection<String> numbers);

    long countByOwnerUserId(Long ownerUserId);

    void deleteByOwnerUserId(Long ownerUserId);

    Optional<SavedNumber> findByIdAndOwnerUserId(Long id, Long ownerUserId);
}
