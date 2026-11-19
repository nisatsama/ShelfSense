package com.nisatsama.shelfsense.replenishment;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReplenishmentRecommendationRepository extends JpaRepository<ReplenishmentRecommendation, Long> {

    @EntityGraph(attributePaths = {"product", "location"})
    List<ReplenishmentRecommendation> findAllByOrderByUpdatedAtDescIdDesc();

    @EntityGraph(attributePaths = {"product", "location"})
    List<ReplenishmentRecommendation> findByStatusOrderByUpdatedAtDescIdDesc(ReplenishmentStatus status);

    @EntityGraph(attributePaths = {"product", "location"})
    Optional<ReplenishmentRecommendation> findByProductIdAndLocationIdAndStatus(
            Long productId, Long locationId, ReplenishmentStatus status
    );

    @EntityGraph(attributePaths = {"product", "location"})
    Optional<ReplenishmentRecommendation> findDetailedById(Long id);

    long countByStatus(ReplenishmentStatus status);
}
