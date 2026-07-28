package com.kraft.identity;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceClaimRepository extends JpaRepository<DeviceClaim, Long> {

    Optional<DeviceClaim> findByDeviceTokenHash(String deviceTokenHash);
}
