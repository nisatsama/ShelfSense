package com.nisatsama.shelfsense.account.dto;

import com.nisatsama.shelfsense.account.Account;
import com.nisatsama.shelfsense.account.AccountRole;

import java.time.Instant;

public record AccountResponse(
        Long id,
        String loginIdentifier,
        AccountRole role,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getLoginIdentifier(),
                account.getRole(),
                account.isEnabled(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }
}
