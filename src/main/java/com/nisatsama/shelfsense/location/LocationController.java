package com.nisatsama.shelfsense.location;

import com.nisatsama.shelfsense.location.dto.LocationCreateRequest;
import com.nisatsama.shelfsense.location.dto.LocationResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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

    @GetMapping
    public List<LocationResponse> findAll() {
        return locationService.findAll()
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