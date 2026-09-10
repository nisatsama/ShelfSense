package com.nisatsama.shelfsense.location;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class LocationService {

    private final LocationRepository locationRepository;

    public LocationService(LocationRepository locationRepository) {
        this.locationRepository = locationRepository;
    }

    public Location create(Location location) {
        if (locationRepository.existsByCode(location.getCode())) {
            throw new IllegalArgumentException(
                    "Location with code already exists: " + location.getCode()
            );
        }

        return locationRepository.save(location);
    }

    @Transactional(readOnly = true)
    public List<Location> findAll() {
        return locationRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Location findById(Long id) {
        return locationRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Location not found: " + id
                        )
                );
    }
}