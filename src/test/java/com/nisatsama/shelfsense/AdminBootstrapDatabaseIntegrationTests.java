package com.nisatsama.shelfsense;

import com.nisatsama.shelfsense.account.Account;
import com.nisatsama.shelfsense.account.AccountRepository;
import com.nisatsama.shelfsense.account.AccountRole;
import com.nisatsama.shelfsense.account.AdminBootstrapService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

@SpringBootTest
@Transactional
class AdminBootstrapDatabaseIntegrationTests {
    @Autowired AdminBootstrapService bootstrapService;
    @Autowired AccountRepository accounts;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PasswordEncoder passwordEncoder;

    private String testLoginIdentifier;

    @Test
    void provisionsExactlyOneAdminInPostgresAndDoesNotRepeatOrOverwrite() {
        boolean alreadyComplete = Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT completed FROM account_bootstrap_state WHERE id=1", Boolean.class));
        assumeFalse(alreadyComplete || accounts.existsByRole(AccountRole.ADMIN),
                "database already has a completed or previously provisioned administrator");

        testLoginIdentifier = "bootstrap-test-" + UUID.randomUUID() + "@example.com";
        String password = UUID.randomUUID().toString() + UUID.randomUUID();

        assertThat(bootstrapService.provisionInitialAdmin(testLoginIdentifier, password))
                .isEqualTo(AdminBootstrapService.BootstrapResult.CREATED);
        Account created = accounts.findByLoginIdentifier(testLoginIdentifier).orElseThrow();
        assertThat(created.getRole()).isEqualTo(AccountRole.ADMIN);
        assertThat(created.isEnabled()).isTrue();
        assertThat(created.getPasswordHash()).startsWith("$2");
        assertThat(passwordEncoder.matches(password, created.getPasswordHash())).isTrue();
        String originalHash = created.getPasswordHash();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM accounts WHERE role='ADMIN'", Integer.class))
                .isEqualTo(1);

        assertThat(bootstrapService.provisionInitialAdmin(testLoginIdentifier, UUID.randomUUID().toString()))
                .isEqualTo(AdminBootstrapService.BootstrapResult.ALREADY_COMPLETED);
        assertThat(accounts.findByLoginIdentifier(testLoginIdentifier).orElseThrow().getPasswordHash())
                .isEqualTo(originalHash);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM accounts WHERE role='ADMIN'", Integer.class))
                .isEqualTo(1);
    }

    @AfterTransaction
    void bootstrapTestAccountAndMarkerAreRolledBack() {
        if (testLoginIdentifier != null) {
            assertThat(accounts.findByLoginIdentifier(testLoginIdentifier)).isEmpty();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT completed FROM account_bootstrap_state WHERE id=1", Boolean.class)).isFalse();
        }
    }
}
