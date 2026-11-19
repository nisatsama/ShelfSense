package com.nisatsama.shelfsense.account.dto;

import com.nisatsama.shelfsense.account.AccountRole;
import jakarta.validation.constraints.NotNull;

public record AccountRoleUpdateRequest(@NotNull AccountRole role) {
}
