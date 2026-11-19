package com.nisatsama.shelfsense.procurement.dto;

import com.nisatsama.shelfsense.procurement.Supplier;

import java.time.LocalDateTime;

public record SupplierResponse(
        Long id,
        String code,
        String name,
        String contactEmail,
        String phone,
        String address,
        int leadTimeDays,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static SupplierResponse from(Supplier s) {
        return new SupplierResponse(
                s.getId(),
                s.getCode(),
                s.getName(),
                s.getContactEmail(),
                s.getPhone(),
                s.getAddress(),
                s.getLeadTimeDays(),
                s.isActive(),
                s.getCreatedAt(),
                s.getUpdatedAt()
        );
    }
}
