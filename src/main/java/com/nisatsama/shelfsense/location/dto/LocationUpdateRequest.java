package com.nisatsama.shelfsense.location.dto;

import com.nisatsama.shelfsense.location.LocationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LocationUpdateRequest(
        @NotBlank @Size(max = 255) String name,
        @NotNull LocationType type,
        @Size(max = 500) String address
) {
}
