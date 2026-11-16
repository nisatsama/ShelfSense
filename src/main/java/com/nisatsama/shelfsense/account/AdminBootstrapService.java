package com.nisatsama.shelfsense.account;

import java.nio.charset.StandardCharsets;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminBootstrapService {
    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    public AdminBootstrapService(AccountRepository accounts, PasswordEncoder passwordEncoder,
                                 JdbcTemplate jdbcTemplate) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public BootstrapResult provisionInitialAdmin(String suppliedUsername, String suppliedPassword) {
        boolean completed = Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT completed FROM account_bootstrap_state WHERE id = 1 FOR UPDATE", Boolean.class));
        if (completed) return BootstrapResult.ALREADY_COMPLETED;

        if (accounts.existsByRole(AccountRole.ADMIN)) {
            markCompleted();
            return BootstrapResult.ALREADY_COMPLETED;
        }

        String username = Account.normalize(suppliedUsername);
        validateCredentials(username, suppliedPassword);
        if (accounts.findByLoginIdentifier(username).isPresent()) {
            throw new IllegalStateException("Bootstrap login identifier is already assigned to a non-admin account");
        }

        Account admin = new Account();
        admin.setLoginIdentifier(username);
        admin.setPasswordHash(passwordEncoder.encode(suppliedPassword));
        admin.setRole(AccountRole.ADMIN);
        admin.setEnabled(true);
        accounts.saveAndFlush(admin);
        markCompleted();
        return BootstrapResult.CREATED;
    }

    private void validateCredentials(String username, String password) {
        if (username == null || username.isBlank() || username.length() > 254) {
            throw new IllegalArgumentException("Bootstrap administrator username must be 1 to 254 characters");
        }
        if (password == null || password.length() < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Bootstrap administrator password must be at least 12 characters and at most 72 UTF-8 bytes");
        }
    }

    private void markCompleted() {
        jdbcTemplate.update("UPDATE account_bootstrap_state SET completed = TRUE, completed_at = CURRENT_TIMESTAMP WHERE id = 1");
    }

    public enum BootstrapResult { CREATED, ALREADY_COMPLETED }
}
