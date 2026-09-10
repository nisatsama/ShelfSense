package com.nisatsama.shelfsense.location.dto;

import com.nisatsama.shelfsense.location.LocationType;

public record LocationResponse(
        Long id,
        String code,
        String name,
        LocationType type,
        String address
) {
}