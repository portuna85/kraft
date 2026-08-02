package com.kraft.saved;

import com.kraft.common.account.AccountDataDeletionHandler;
import org.springframework.stereotype.Component;

@Component
public class SavedNumberAccountDataDeletionHandler implements AccountDataDeletionHandler {

    private final SavedNumberRepository savedNumberRepository;

    public SavedNumberAccountDataDeletionHandler(SavedNumberRepository savedNumberRepository) {
        this.savedNumberRepository = savedNumberRepository;
    }

    @Override
    public void deleteForAccount(Long userId) {
        savedNumberRepository.deleteByOwnerUserId(userId);
    }
}
