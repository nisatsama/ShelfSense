package com.nisatsama.shelfsense.procurement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    boolean existsByCode(String code);
    Optional<Supplier> findByCode(String code);
}
