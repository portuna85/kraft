package com.kraft.common.account;

/** Deletes private data owned by an account within the caller's transaction. */
public interface AccountDataDeletionHandler {

    void deleteForAccount(Long userId);
}
