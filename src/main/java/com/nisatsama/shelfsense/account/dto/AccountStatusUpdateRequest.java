package com.nisatsama.shelfsense.account.dto;

import jakarta.validation.constraints.NotNull;

public record AccountStatusUpdateRequest(@NotNull Boolean enabled) {
}
