package com.nisatsama.shelfsense.forecast;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DemandForecastRepository extends JpaRepository<DemandForecast, Long> {

    @EntityGraph(attributePaths = {"product", "location"})
    List<DemandForecast> findAllByOrderByGeneratedAtDescIdDesc();

    @EntityGraph(attributePaths = {"product", "location"})
    List<DemandForecast> findByProductIdAndLocationIdOrderByGeneratedAtDescIdDesc(Long productId, Long locationId);

    @EntityGraph(attributePaths = {"product", "location"})
    List<DemandForecast> findByProductIdOrderByGeneratedAtDescIdDesc(Long productId);

    @EntityGraph(attributePaths = {"product", "location"})
    List<DemandForecast> findByLocationIdOrderByGeneratedAtDescIdDesc(Long locationId);

    Optional<DemandForecast> findTopByProductIdAndLocationIdOrderByGeneratedAtDescIdDesc(Long productId, Long locationId);
}
