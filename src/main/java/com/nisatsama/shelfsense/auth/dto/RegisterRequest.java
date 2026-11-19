package com.nisatsama.shelfsense.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 254) String loginIdentifier,
        @NotBlank @Size(min = 8, max = 72) String password
) {
}
