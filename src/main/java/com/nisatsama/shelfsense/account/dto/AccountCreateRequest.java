package com.nisatsama.shelfsense.account.dto;

import com.nisatsama.shelfsense.account.AccountRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AccountCreateRequest(
        @NotBlank @Size(max = 254) String loginIdentifier,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotNull AccountRole role
) {
}
