package com.nisatsama.shelfsense.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(@NotBlank @Size(max = 254) String loginIdentifier,
                           @NotBlank @Size(max = 72) String password) { }
