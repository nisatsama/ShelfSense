package com.nisatsama.shelfsense.account;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByLoginIdentifier(String loginIdentifier);
    boolean existsByRole(AccountRole role);
}
