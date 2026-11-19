package com.nisatsama.shelfsense.procurement.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SupplierRequest(
        @NotBlank @Size(max = 100) String code,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 255) String contactEmail,
        @Size(max = 60) String phone,
        @Size(max = 500) String address,
        @Min(1) Integer leadTimeDays,
        Boolean active
) {
}
