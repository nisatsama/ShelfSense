package com.nisatsama.shelfsense.location;

import com.nisatsama.shelfsense.common.ResourceNotFoundException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

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

    public Location update(Long id, String name, LocationType type, String address) {
        Location location = locationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Location not found: " + id));
        location.setName(name);
        location.setType(type);
        location.setAddress(address);
        return locationRepository.save(location);
    }

    @Transactional(readOnly = true)
    public List<Location> findAll() {
        return locationRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
    }

    @Transactional(readOnly = true)
    public List<Location> search(LocationType type, String query) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return findAll().stream()
                .filter(l -> type == null || l.getType() == type)
                .filter(l -> normalizedQuery.isEmpty()
                        || l.getCode().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                        || l.getName().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                        || (l.getAddress() != null && l.getAddress().toLowerCase(Locale.ROOT).contains(normalizedQuery)))
                .toList();
    }

    @Transactional(readOnly = true)
    public Location findById(Long id) {
        return locationRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Location not found: " + id
                        )
                );
    }
}
