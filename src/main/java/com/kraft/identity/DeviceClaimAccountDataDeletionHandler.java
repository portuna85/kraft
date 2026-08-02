package com.kraft.identity;

import com.kraft.common.account.AccountDataDeletionHandler;
import org.springframework.stereotype.Component;

@Component
public class DeviceClaimAccountDataDeletionHandler implements AccountDataDeletionHandler {

    private final DeviceClaimRepository deviceClaimRepository;

    public DeviceClaimAccountDataDeletionHandler(DeviceClaimRepository deviceClaimRepository) {
        this.deviceClaimRepository = deviceClaimRepository;
    }

    @Override
    public void deleteForAccount(Long userId) {
        deviceClaimRepository.deleteByClaimedByUserId(userId);
    }
}
