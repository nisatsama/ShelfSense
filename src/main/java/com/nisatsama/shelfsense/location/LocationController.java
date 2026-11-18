package com.nisatsama.shelfsense.location;

import com.nisatsama.shelfsense.location.dto.LocationCreateRequest;
import com.nisatsama.shelfsense.location.dto.LocationResponse;
import com.nisatsama.shelfsense.location.dto.LocationUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/locations")
public class LocationController {

    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public LocationResponse create(
            @Valid @RequestBody LocationCreateRequest request
    ) {
        Location location = new Location();

        location.setCode(request.code());
        location.setName(request.name());
        location.setType(request.type());
        location.setAddress(request.address());

        Location saved = locationService.create(location);

        return toResponse(saved);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public LocationResponse update(
            @PathVariable Long id,
            @Valid @RequestBody LocationUpdateRequest request
    ) {
        Location updated = locationService.update(id, request.name(), request.type(), request.address());
        return toResponse(updated);
    }

    @GetMapping
    public List<LocationResponse> findAll(
            @RequestParam(required = false) LocationType type,
            @RequestParam(required = false) String q
    ) {
        return locationService.search(type, q)
                .stream()
                .map(LocationController::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    public LocationResponse findById(@PathVariable Long id) {
        return toResponse(locationService.findById(id));
    }

    private static LocationResponse toResponse(Location location) {
        return new LocationResponse(
                location.getId(),
                location.getCode(),
                location.getName(),
                location.getType(),
                location.getAddress()
        );
    }
}
